package org.apache.lucene.postProcess;	
/** Class representing feedback documents, pseudo- or otherwise.
  * @since 3.0
  */
public class FeedbackDocuments
{
	public int docid[];
	public int rank[];
	public float score[];
	public float totalDocumentLength;
}
