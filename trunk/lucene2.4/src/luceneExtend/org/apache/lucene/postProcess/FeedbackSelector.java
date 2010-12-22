
package org.apache.lucene.postProcess;

import org.apache.lucene.search.Searcher;


/**
 * Implements of this class can be used to select feedback documents. Feedback
 * documents are represented by the FeedbackDocument instances.
 */
public abstract class FeedbackSelector {

	protected Searcher searcher;
	protected String field;
	protected int effDocuments;

	/** Set the index to be used */
	public void setIndex(Searcher searcher) {
		this.searcher = searcher;
	}
	public void setField(String field){
		this.field = field;
	}
	public static String getTrimID(String queryid) {
		boolean firstNumericChar = false;
		StringBuilder queryNoTmp = new StringBuilder();
		for (int i = queryid.length() - 1; i >= 0; i--) {
			char ch = queryid.charAt(i);
			if (Character.isDigit(ch)) {
				queryNoTmp.append(queryid.charAt(i));
				firstNumericChar = true;
			} else if (firstNumericChar)
				break;
		}
		return "" + Integer.parseInt(queryNoTmp.reverse().toString());
	}
	/** Obtain feedback documents for the specified query request */
	public abstract FeedbackDocuments getFeedbackDocuments(String topicId);
	public abstract String getInfo();
	public void setExpDocuments(int effDocuments) {
		this.effDocuments = effDocuments;
		
	}
}
