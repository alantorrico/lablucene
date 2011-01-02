package org.apache.lucene.postProcess;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TObjectIntHashMap;

import java.io.IOException;
import java.util.Arrays;

import org.apache.log4j.Logger;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.search.RBooleanQuery;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.TopDocCollector;
import org.apache.lucene.search.model.Idf;
import org.dutir.lucene.util.ApplicationSetup;

/**
 * implement the Mitra document Reranking formula: Mitra et al. Improving
 * Automatic Query Expansion parameter: 1.
 * MitraReranking.IDF_WITHOUT_CORRELATION: strBoolean, default = false; 2.
 * MitraReranking.Reranking_Tag: 3. MitraReranking.LocalDFOrGlobalIDF: default,
 * true(local) 4. MitraReranking.desdenting 5. MitraReranking.rerankNum
 * 
 * @author YeZheng
 * 
 */
public class MitraReRankingPostProcess extends QueryExpansion {

	protected static Logger logger = Logger
			.getLogger("MitraReRankingPostProcess");
	protected String queryID;

	protected boolean IDF_WITHOUT_CORRELATION = Boolean
			.parseBoolean(ApplicationSetup.getProperty(
					"MitraReranking.IDF_WITHOUT_CORRELATION", "false"));
	protected boolean Mitra_Reranking_Tag = Boolean
			.parseBoolean(ApplicationSetup.getProperty(
					"MitraReranking.Reranking_Tag", "false"));
	protected int Mitra_Reranking_rerankNum = Integer.parseInt(ApplicationSetup
			.getProperty("MitraReranking.rerankNum", "100"));
	// TIntIntHashMap term_id_map = null;
	TObjectIntHashMap<String> term_id_map = null;

	float idfs[];
	int tt_cooccur_count[][];
	TIntArrayList[] QueryTermsOccurred;
	private int set_size;
	private int[] docids;
	private float[] scores;
	private short[] occurences;
	public TermFreqVector t_tfs_cache[] = null;
	float mitra_socres[];
	private boolean qeTag = true;

	public MitraReRankingPostProcess() {
		super();
		reset();
	}

	public void setQETag(boolean tag) {
		this.qeTag = tag;
	}

	public void reset() {
		qeTag = true;
		idfs = null;
		tt_cooccur_count = null;
		set_size = 0;
		docids = null;
		occurences = null;
		t_tfs_cache = null;
		mitra_socres = null;
		scores = null;
		QueryTermsOccurred = null;
	}

	public String getInfo() {
		if (IDF_WITHOUT_CORRELATION) {
			return "MitraReRanking_" + "idfOnly" + "_Mitra_Reranking_Tag_"
					+ Mitra_Reranking_Tag + "_Num=" + Mitra_Reranking_rerankNum;
		} else {
			return "MitraReRanking_" + "withCorrelation"
					+ "_Mitra_Reranking_Tag_" + Mitra_Reranking_Tag + "_Num="
					+ Mitra_Reranking_rerankNum;
		}

	}

	/**
	 * compute the mitra Scores and cache up
	 * 
	 * @throws Exception
	 */
	public void pre_process() throws Exception {
		logger.info("Starting MitraReRankingPostProcess post-processing.");
		// Index index = manager.getIndex();
		// documentIndex = index.getDocumentIndex();
		// invertedIndex = index.getInvertedIndex();
		// lexicon = index.getLexicon();
		// collStats =
		int num_doc = this.searcher.maxDoc();

		// this.request = (Request)q;

		// ResultSet resultSet = q.getResultSet();
		set_size = this.ScoreDoc.length;
		docids = new int[set_size];
		scores = new float[set_size];
		for (int i = 0; i < set_size; i++) {
			docids[i] = this.ScoreDoc[i].doc;
			scores[i] = this.ScoreDoc[i].score;
		}

		// occurences = resultSet.getOccurrences();

		t_tfs_cache = new TermFreqVector[set_size];

		String[] terms = this.termSet.toArray(new String[0]);
		int terms_size = terms.length;

		this.idfs = new float[terms_size];
		this.term_id_map = new TObjectIntHashMap<String>();
		this.tt_cooccur_count = new int[terms_size][terms_size];

		for (int i = 0; i < terms.length; i++) {

			// this.term_id_map.put("" + lEntry.termId, i);
			this.term_id_map.put(terms[i], i);
			Term term = new Term(field, terms[i]);
			// float TF = searcher.termFreq(term);
			float Nt = searcher.docFreq(term);

			idfs[i] = Idf.log(((double) num_doc - (double) Nt + 0.5d)
					/ ((double) Nt + 0.5d));
		}

		// this.QueryTermsOccurred = new ArrayList[set_size];
		this.QueryTermsOccurred = new TIntArrayList[set_size];
		for (int i = 0; i < set_size; i++) {
			this.QueryTermsOccurred[i] = new TIntArrayList();

			TermFreqVector tfv = null;
			try {
				tfv = this.searcher.getIndexReader().getTermFreqVector(
						docids[i], field);
				t_tfs_cache[i] = tfv;
			} catch (IOException e) {
				e.printStackTrace();
			}
			String strterms[] = tfv.getTerms();
			int freqs[] = tfv.getTermFrequencies();
			for (int k = 0; k < strterms.length; k++) {
				if (this.term_id_map.contains(strterms[k])) {
					int vl = this.term_id_map.get(strterms[k]);
					update_tt_cooccur_count(this.QueryTermsOccurred[i], vl);
					this.QueryTermsOccurred[i].add(vl);
				}
			}
		}

		if (ApplicationSetup.getProperty("MitraReranking.LocalDFOrGlobalIDF",
				"false").endsWith("true")) {
			float tt[] = new float[terms_size];
			for (int i = 0; i < terms_size; i++) {
				tt[i] = this.tt_cooccur_count[i][i];
			}
			re_rank(this.QueryTermsOccurred, tt);
		} else {
			re_rank(this.QueryTermsOccurred, this.idfs);
		}

		computeMitra_Scores(scores, this.QueryTermsOccurred, set_size);
	}

	public TopDocCollector postProcess(RBooleanQuery query,
			TopDocCollector topDoc, Searcher seacher) {
		setup(query, topDoc, seacher); // it is necessary
		try {
			pre_process();
		} catch (Exception e) {
			e.printStackTrace();
		}
		assignValues(scores, this.QueryTermsOccurred, set_size);

		// if qeTag = true; we only keep top Mitra_Reranking_rerankNum docs.
		// Otherwise, all for
		int reRankNum = qeTag ? Math.min(Mitra_Reranking_rerankNum, set_size)
				: set_size;

		int num = Integer.parseInt(ApplicationSetup.getProperty(
				"TRECQuerying.endFeedback", "1000"));
		TopDocCollector cls = new TopDocCollector(num);
		cls.setInfo(topDoc.getInfo());
		cls.setInfo_add(this.getInfo());
//		logger.info("Reranked docs: " + reRankNum + ", " + qeTag);
		for (int i = 0; i < reRankNum; i++) {
			cls.collect(this.docids[i], this.scores[i]);
		}
		return cls;
	}

	public float[] getMitraScores() {
		return mitra_socres;
	}

	final void re_rank(TIntArrayList[] queryTermList, float[] tt) {

		for (int i = 0; i < queryTermList.length; i++) {
			int len = queryTermList[i].size();
			if (len < 0) {
				continue;
			}
			for (int j = 0; j < len; j++) {
				int posMin = j;
				int idMin = queryTermList[i].get(j);
				for (int k = j + 1; k < len; k++) {
					int id = queryTermList[i].get(k);
					if (tt[id] > tt[idMin]) {
						posMin = k;
						idMin = id;
					}
				}
				int tmpInt = queryTermList[i].get(j);
				queryTermList[i].set(j, queryTermList[i].get(posMin));
				queryTermList[i].set(posMin, tmpInt);
			}
		}

		// for(int i =0; i < queryTermList.length; i++){
		// int len = queryTermList[i].size();
		// if(len < 0){
		// continue;
		// }
		// System.out.print(i + ": ");
		// for(int j=0; j < len; j++){
		// int idMin = queryTermList[i].get(j);
		// System.out.print( tt[idMin] +" \t");
		// }
		// System.out.println();
		// }
	}

	private void computeMitra_Scores(float[] scores,
			TIntArrayList[] queryTermsOccurred, int set_size) {
		mitra_socres = new float[set_size];
		Arrays.fill(mitra_socres, 0);
		for (int i = 0; i < set_size; i++) {
			float tempScore = 0;
			for (int j = 0, len = queryTermsOccurred[i].size(); j < len; j++) {
				if (this.IDF_WITHOUT_CORRELATION) {
					tempScore += this.idfs[queryTermsOccurred[i].get(j)];
				} else {
					if (j > 0) {
						float tidf = this.idfs[queryTermsOccurred[i].get(j)];
						tempScore = tempScore + tidf
								* getCorrelationScore(queryTermsOccurred[i], j);
					} else {
						tempScore = this.idfs[queryTermsOccurred[i].get(j)];
					}
				}
			}
			mitra_socres[i] = tempScore;
		}
		// System.out.println();
	}

	private void assignValues(float[] scores,
			TIntArrayList[] queryTermsOccurred, int set_size) {
		int pos = -1;
		// if (this.Mitra_Reranking_Tag) {
		pos = findMax(mitra_socres);
		// }
		float max = mitra_socres[pos];
		float smax = scores[0];
		for (int i = 0; i < set_size; i++) {
			if (this.Mitra_Reranking_Tag) {
				// scores[i] = Math.sqrt(mitra_socres[i] / max)
				// * scores[i];
				scores[i] = (1 - r) * mitra_socres[i] / max + r * scores[i]
						/ smax;
			} else {
				scores[i] = mitra_socres[i] + 1 / (float) (i + 1);
				// System.out.println(mitra_socres[i] + ", " + scores[i]);
			}
		}
	}

	private float r = Float.parseFloat(ApplicationSetup.getProperty(
			"MitraReranking.r", "0.5"));

	final int findMax(float arrays[]) {
		float max = arrays[0];
		int pos = 0;
		for (int i = 1; i < arrays.length; i++) {
			if (max < arrays[i]) {
				pos = i;
				max = arrays[i];
			}
		}
		return pos;
	}

	private float getCorrelationScore(TIntArrayList arrayList, int j) {
		float max = 0;
		int pos = arrayList.get(j);
		for (int i = 0; i < j; i++) {
			int id1 = arrayList.get(i);
			int s, b;
			if (id1 < pos) {
				s = id1;
				b = pos;
			} else {
				s = pos;
				b = id1;
			}
			float score = this.tt_cooccur_count[s][b]
					/ (float) this.tt_cooccur_count[id1][id1];
			if (score > max) {
				max = score;
			}
		}
		return 1 - max;
	}

	private void update_tt_cooccur_count(TIntArrayList arrayList, int intValue) {
		this.tt_cooccur_count[intValue][intValue]++;
		int size = arrayList.size();
		for (int i = 0; i < size; i++) {
			int id = arrayList.get(i);
			int min, max;
			if (id < intValue) {
				min = id;
				max = intValue;
			} else {
				min = intValue;
				max = id;
			}
			this.tt_cooccur_count[min][max]++;
		}
	}

}
