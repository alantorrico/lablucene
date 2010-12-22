
package org.apache.lucene.postProcess;
import gnu.trove.TIntHashSet;

import org.apache.log4j.Logger;
import org.apache.lucene.search.ScoreDoc;
import org.dutir.lucene.evaluation.TRECQrelsInMemory;
import org.dutir.lucene.util.ApplicationSetup;
/** A feedback selector for pseudo-relevance feedback. Selects the top ApplicationSetup.EXPANSION_DOCUMENTS
  * documents from the ResultSet attached to the specified request.
  */
public class PseudoRelevanceFeedbackSelector extends FeedbackSelector
{
	Logger logger = Logger.getLogger(PseudoRelevanceFeedbackSelector.class);
	private ScoreDoc[] ScoreDoc;
	static boolean Relevance = Boolean.parseBoolean(ApplicationSetup
			.getProperty("QueryExpansion.RelevanceFeedback", "false"));
	static TRECQrelsInMemory trecR = null;
	
	public static TRECQrelsInMemory getTRECQerls() {
		if (trecR == null) {
			trecR = new TRECQrelsInMemory();
		}
		return trecR;
	}
	
	public PseudoRelevanceFeedbackSelector(){}
	
	public FeedbackDocuments getFeedbackDocuments(String topicId)
	{
		
		int docIds[] = new int[0];
		float scores[] = new float[0];
		int maxPos = 0; 
		if (Relevance) {
			if (trecR == null) {
				trecR = new TRECQrelsInMemory();
			}
			int pos = 0;
			try {
				String relDocs[] = trecR
						.getRelevantDocumentsToArray(getTrimID(topicId));
				if (relDocs == null) {
					logger.warn("no relevance doc for query: " + topicId);
					maxPos = 0;
				}else{
					maxPos = Math.min(effDocuments, relDocs.length);
					docIds = new int[maxPos];
					scores = new float[maxPos];
					
					if(ApplicationSetup.Eval_ID){
						for (int i = 0; i < relDocs.length && pos < maxPos; i++) {
							int _id = 0;
							try {
								_id = Integer.parseInt(relDocs[i]);
							} catch (Exception e) {
								logger.warn("false inner doc number: ", e);
								logger.warn ("false doc: " + relDocs[i]);
								continue;
							}
							int docid = _id;
							docIds[pos] = docid;
							scores[pos] = 1;
							pos++;
						}
					}else{
						throw new UnsupportedOperationException();
					}
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			maxPos = effDocuments;
			if(maxPos > this.ScoreDoc.length){
				logger.warn("there is no sufficient feedback docs for Query " + topicId + ", " + maxPos + ":"+ this.ScoreDoc.length);
			}
			maxPos = Math.min(effDocuments, this.ScoreDoc.length);
			docIds = new int[maxPos];
			scores = new float[maxPos];
			for (int i = 0; i < maxPos; i++) {
				docIds[i] = this.ScoreDoc[i].doc;
				scores[i] = this.ScoreDoc[i].score;
			}
		}
		FeedbackDocuments fdocs = new FeedbackDocuments();
		fdocs.totalDocumentLength= 0;
		fdocs.docid = docIds;
		fdocs.score = scores;
		for (int i = 0; i < maxPos; i++) {
			fdocs.totalDocumentLength += searcher.getFieldLength(field,
					docIds[i]);
		}
		return fdocs;
	}
	
	public void setTopDocs(ScoreDoc[] ScoreDoc){
		this.ScoreDoc = ScoreDoc;
	}

	@Override
	public String getInfo() {
		return "";
	}
	
}
