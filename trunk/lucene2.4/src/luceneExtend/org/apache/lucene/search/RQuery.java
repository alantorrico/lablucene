package org.apache.lucene.search;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Query;
/** The abstract base class for queries.
    <p>Instantiable subclasses are:
    <ul>
    <li> {@link TermQuery}
    <li> {@link MultiTermQuery}
    <li> {@link BooleanQuery}
    <li> {@link WildcardQuery}
    <li> {@link PhraseQuery}
    <li> {@link PrefixQuery}
    <li> {@link MultiPhraseQuery}
    <li> {@link FuzzyQuery}
    <li> {@link RangeQuery}
    <li> {@link org.apache.lucene.search.spans.SpanQuery}
    </ul>
    <p>A parser for queries is contained in:
    <ul>
    <li>{@link org.apache.lucene.queryParser.QueryParser QueryParser}
    </ul>
*/

public abstract class RQuery  extends Query{
  private float boost = 1.0f;                     // query boost factor

  //added yezheng 
  private float occurNum = 1.0f;
  /** Sets the boost for this query clause to <code>b</code>.  Documents
   * matching this clause will (in addition to the normal weightings) have
   * their score multiplied by <code>b</code>.
   */
  
  String topicid = null;
  
  public void setID (String id){
	  this.topicid = id;
  }
  public String getTopicId()
  {
	  return this.topicid;
  }
  
  public void setBoost(float b) { boost = b; }

  public void setOccurNum(float num){occurNum = num; }
  
  public void addOccurNum(){
	  this.occurNum++;
  }
  
  public float getOccurNum(){
	  return occurNum;
  }
  
  /** Gets the boost for this clause.  Documents matching
   * this clause will (in addition to the normal weightings) have their score
   * multiplied by <code>b</code>.   The boost is 1.0 by default.
   */
  public float getBoost() { return boost; }

  /** Prints a query to a string, with <code>field</code> assumed to be the 
   * default field and omitted.
   * <p>The representation used is one that is supposed to be readable
   * by {@link org.apache.lucene.queryParser.QueryParser QueryParser}. However,
   * there are the following limitations:
   * <ul>
   *  <li>If the query was created by the parser, the printed
   *  representation may not be exactly what was parsed. For example,
   *  characters that need to be escaped will be represented without
   *  the required backslash.</li>
   * <li>Some of the more complicated queries (e.g. span queries)
   *  don't have a representation that can be parsed by QueryParser.</li>
   * </ul>
   */
  public abstract String toString(String field);

  /** Prints a query to a string. */
  public String toString() {
    return toString("");
  }

  /** Expert: Constructs an appropriate Weight implementation for this query.
   *
   * <p>Only implemented by primitive queries, which re-write to themselves.
   */
  protected Weight createWeight(Searcher searcher) throws IOException {
    throw new UnsupportedOperationException();
  }

  /** Expert: Constructs and initializes a Weight for a top-level query. */
  public Weight weight(Searcher searcher)
    throws IOException {
    Query query = searcher.rewrite(this);
    Weight weight = query.createWeight(searcher);
    float sum = weight.sumOfSquaredWeights();
    float norm = getSimilarity(searcher).queryNorm(sum);
    weight.normalize(norm);
//    System.out.println(weight.getValue());
    return weight;
  }

  /** Expert: called to re-write queries into primitive queries. For example,
   * a PrefixQuery will be rewritten into a BooleanQuery that consists
   * of TermQuerys.
   */
  public RQuery rewrite(IndexReader reader) throws IOException {
    return this;
  }

  /** Expert: called when re-writing queries under MultiSearcher.
   *
   * Create a single query suitable for use by all subsearchers (in 1-1
   * correspondence with queries). This is an optimization of the OR of
   * all queries. We handle the common optimization cases of equal
   * queries and overlapping clauses of boolean OR queries (as generated
   * by MultiTermQuery.rewrite() and RangeQuery.rewrite()).
   * Be careful overriding this method as queries[0] determines which
   * method will be called and is not necessarily of the same type as
   * the other queries.
  */
  public Query combine(Query[] queries) {
    HashSet uniques = new HashSet();
    for (int i = 0; i < queries.length; i++) {
      Query query = queries[i];
      BooleanClause[] clauses = null;
      // check if we can split the query into clauses
      boolean splittable = (query instanceof BooleanQuery);
      if(splittable){
        BooleanQuery bq = (BooleanQuery) query;
        splittable = bq.isCoordDisabled();
        clauses = bq.getClauses();
        for (int j = 0; splittable && j < clauses.length; j++) {
          splittable = (clauses[j].getOccur() == BooleanClause.Occur.SHOULD);
        }
      }
      if(splittable){
        for (int j = 0; j < clauses.length; j++) {
          uniques.add(clauses[j].getQuery());
        }
      } else {
        uniques.add(query);
      }
    }
    // optimization: if we have just one query, just return it
    if(uniques.size() == 1){
        return (Query)uniques.iterator().next();
    }
    Iterator it = uniques.iterator();
    BooleanQuery result = new BooleanQuery(true);
    while (it.hasNext())
      result.add((Query) it.next(), BooleanClause.Occur.SHOULD);
    return result;
  }

  /**
   * Expert: adds all terms occuring in this query to the terms set. Only
   * works if this query is in its {@link #rewrite rewritten} form.
   * 
   * @throws UnsupportedOperationException if this query is not yet rewritten
   */
  public void extractTerms(Set terms) {
    // needs to be implemented by query subclasses
    throw new UnsupportedOperationException();
  }


  /** Expert: merges the clauses of a set of BooleanQuery's into a single
   * BooleanQuery.
   *
   *<p>A utility for use by {@link #combine(Query[])} implementations.
   */
  public static RQuery mergeBooleanQueries(Query[] queries) {
    HashSet allClauses = new HashSet();
    for (int i = 0; i < queries.length; i++) {
      RBooleanClause[] clauses = ((RBooleanQuery)queries[i]).getClauses();
      for (int j = 0; j < clauses.length; j++) {
        allClauses.add(clauses[j]);
      }
    }

    boolean coordDisabled =
      queries.length==0? false : ((RBooleanQuery)queries[0]).isCoordDisabled();
    RBooleanQuery result = new RBooleanQuery(coordDisabled);
    Iterator i = allClauses.iterator();
    while (i.hasNext()) {
      result.add((RBooleanClause)i.next());
    }
    return result;
  }

  /** Expert: Returns the Similarity implementation to be used for this query.
   * Subclasses may override this method to specify their own Similarity
   * implementation, perhaps one that delegates through that of the Searcher.
   * By default the Searcher's Similarity implementation is returned.*/
  public Similarity getSimilarity(Searcher searcher) {
    return searcher.getSimilarity();
  }

  /** Returns a clone of this query. */
  public Object clone(){
    return (RQuery)super.clone();
  }
}
