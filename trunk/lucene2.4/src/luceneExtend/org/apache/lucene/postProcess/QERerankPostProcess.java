package org.apache.lucene.postProcess;


import java.io.IOException;
import java.util.Arrays;

import org.apache.lucene.search.RBooleanQuery;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.TopDocCollector;
import org.dutir.lucene.util.ApplicationSetup;

import gnu.trove.THashMap;
import gnu.trove.TIntDoubleHashMap;


/**
 * Fun: expand the query based on Terrier's build-in QE method, and then use
 * the expanded query to rerank the results obtained from the first round
 * retrieval
 * 
 * @author YeZheng
 * 
 */
public class QERerankPostProcess extends QueryExpansion {

	int effDocumentsNum = Integer.parseInt(ApplicationSetup.getProperty(
			"QERerankPostProcess.effDocumentsNum", "5"));
	int numberOfTermsAsFeatures = Integer.parseInt(ApplicationSetup
			.getProperty("QERerankPostProcess.numberOfTermsAsFeatures", "100"));
	public TIntDoubleHashMap qeScoresMap = new TIntDoubleHashMap();
	public TIntDoubleHashMap idfMap = new TIntDoubleHashMap();
	public TIntDoubleHashMap keyFMap = new TIntDoubleHashMap();

	double qeScores[];

	private double k_1 = 1.2d;
	private double b = 0.75d;
	private double k_3 = 8d;
	protected float averageDocumentLength;
	float numberOfDocuments;

	void reset() {
		qeScores = null;
		qeScoresMap.clear();
		idfMap.clear();
		keyFMap.clear();
	}

	public String getInfo() {
		if (QEModel != null)
			return "QERerankPostProcess_" + QEModel.getInfo()
					+ "_effDocumentsNum=" + effDocumentsNum
					+ "_numberOfTermsAsFeatures=" + numberOfTermsAsFeatures;
		return "";
	}

	public void setup() {
		averageDocumentLength = this.searcher.getAverageLength(field);
		try {
			numberOfDocuments = this.searcher.maxDoc();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public TopDocCollector postProcess(RBooleanQuery query,
			TopDocCollector topDoc, Searcher seacher) {
		reset();
		setup();


		logger.info("Starting query expansion post-processing.");
		// get the query expansion model to use
		
		int set_size = this.ScoreDoc.length;
		int docids[] = new int[set_size];
		float scores[] = new float[set_size];
		for (int i = 0; i < set_size; i++) {
			docids[i] = this.ScoreDoc[i].doc;
			scores[i] = this.ScoreDoc[i].score;
		}
		
		// get the expanded query terms
		expandQuery(queryTerms, resultSet);

		qeScores = new double[set_size];
		Arrays.fill(qeScores, 0);
		// reranking the documents
		for (int i = 0; i < set_size; i++) {
			int docLength = documentIndex.getDocumentLength(docids[i]);
			int[][] terms = directIndex.getTerms(docids[i]);
			for (int k = 0; k < terms[0].length; k++) {
				int termid = terms[0][k];
				int termtf = terms[1][k];
				if (qeScoresMap.contains(termid)) {
					// qeScoresMap.get(termid)
					double keyFrequency = qeScoresMap.get(termid);

					qeScores[i] += idfMap.get(termid)
							* ((k_3 + 1) * keyFrequency / (k_3 + keyFrequency))
							* ((k_1 + 1d) * termtf 
							/ (k_1* ((1 - b) + b * docLength/ averageDocumentLength) + termtf));
				}
			}
			scores[i] = qeScores[i];
		}
		HeapSort.descendingHeapSort(scores, docids, occurences, set_size);
	}

	public double getScorce(int termid, int termtf, int docLength) {
		double keyFrequency = qeScoresMap.get(termid);
		double retV = idfMap.get(termid)
				* ((k_3 + 1) * keyFrequency / (k_3 + keyFrequency))
				* ((k_1 + 1d) * termtf / (k_1
						* ((1 - b) + b * docLength / averageDocumentLength) + termtf));
		return retV;
	}

	public void expandQuery(MatchingQueryTerms query, ResultSet resultSet) {
		expandQuery(query, resultSet, effDocumentsNum);
	}

	public ExpansionTerms expandQuery(MatchingQueryTerms query,
			ResultSet resultSet, int epNum) {
		// the number of term to re-weight (i.e. to do relevance feedback) is
		// the maximum between the system setting and the actual query length.
		// if the query length is larger than the system setting, it does not
		// make sense to do relevance feedback for a portion of the query.
		// Therefore,
		// we re-weight the number of query length of terms.

		// If no document retrieved, keep the original query.
		if (resultSet.getResultSize() == 0) {
			return null;
		}

		int[] docIDs = resultSet.getDocids();
		double[] scores = resultSet.getScores();
		double totalDocumentLength = 0;

		// if the number of retrieved documents is lower than the parameter
		// EXPANSION_DOCUMENTS, reduce the number of documents for expansion
		// to the number of retrieved documents.
		int effDocuments = Math.min(docIDs.length, effDocumentsNum);
		for (int i = 0; i < effDocuments; i++) {
			totalDocumentLength += documentIndex.getDocumentLength(docIDs[i]);
			if (logger.isDebugEnabled()) {
				logger.debug((i + 1) + ": "
						+ documentIndex.getDocumentNumber(docIDs[i]) + " ("
						+ docIDs[i] + ") with " + scores[i]);
			}
		}
		ExpansionTerms expansionTerms = new ExpansionTerms(collStats,
				totalDocumentLength, lexicon);

		for (int i = 0; i < epNum; i++) {
			int[][] terms = directIndex.getTerms(docIDs[i]);
			if (terms == null)
				logger.warn("document "
						+ documentIndex.getDocumentLength(docIDs[i]) + "("
						+ docIDs[i] + ") not found");
			else
				for (int j = 0; j < terms[0].length; j++)
					expansionTerms
							.insertTerm(terms[0][j], (double) terms[1][j]);
		}

		expansionTerms.setOriginalQueryTerms(query);
		SingleTermQuery[] expandedTerms = expansionTerms.getExpandedTerms(
				numberOfTermsAsFeatures, QEModel);

		for(String term: query.getTerms()){
			LexiconEntry entry = lexicon.getLexiconEntry(term);
				int n_t = entry.n_t;
				qeScoresMap.put(entry.termId, query.getTermWeight(term));
				idfMap.put(entry.termId, Idf.log((numberOfDocuments - n_t + 0.5d)
						/ (n_t + 0.5d)));
		}
		
		for (int i = 0; i < expandedTerms.length; i++) {
			SingleTermQuery expandedTerm = expandedTerms[i];
			String strTerm = expandedTerm.getTerm();
//			if(query.getTermWeight(strTerm) > 0){
//				continue;
//			}
			LexiconEntry entry = lexicon.getLexiconEntry(strTerm);
			int n_t = entry.n_t;
			
			qeScoresMap.adjustOrPutValue(entry.termId, expandedTerm.getWeight(), expandedTerm.getWeight());
			idfMap.put(entry.termId, Idf.log((numberOfDocuments - n_t + 0.5d)
					/ (n_t + 0.5d)));
		}
		

		return expansionTerms;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		QERerankPostProcess qer = new QERerankPostProcess();
		qer.qeScoresMap.put(1225, 1225);
		qer.qeScoresMap.put(294, 1225);
		qer.qeScoresMap.put(1007, 1225);
		qer.qeScoresMap.put(536, 1225);
		if (qer.qeScoresMap.contains(3373)) {
			System.out.println("bug");
		}
	}

}
