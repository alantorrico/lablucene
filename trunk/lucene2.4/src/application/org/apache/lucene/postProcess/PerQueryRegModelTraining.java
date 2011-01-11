package org.apache.lucene.postProcess;

import java.io.IOException;

import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RBooleanClause;
import org.apache.lucene.search.RBooleanQuery;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.TopDocCollector;
import org.dutir.lucene.evaluation.AdhocEvaluation;
import org.dutir.lucene.evaluation.TRECQrelsInMemory;
import org.dutir.lucene.util.ApplicationSetup;
import org.dutir.lucene.util.ExpansionTerms;
import org.dutir.lucene.util.ExpansionTerms.ExpansionTerm;

import weka.classifiers.Classifier;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.functions.LibSVM;
import weka.classifiers.functions.SVMreg;
import weka.classifiers.functions.supportVector.NormalizedPolyKernel;
import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SelectedTag;

public class PerQueryRegModelTraining extends QueryExpansion {
	
	private static AdhocEvaluation trecR =null;
	public static AdhocEvaluation getTRECQerls() {
		if (trecR  == null) {
			trecR = new AdhocEvaluation();
		}
		return trecR;
	}
	/**
	 * todo
	 * 
	 * @param classfierName
	 * @return
	 */
	private static Classifier getClassifier(String classfierName) {
		if (classfierName.equals("NaiveBayes")) {
			return (Classifier) new NaiveBayes();
		} else if (classfierName.equals("SVM")
				|| classfierName.equals("weka.classifiers.functions.LibSVM")) {
			LibSVM svm = new LibSVM();
			svm.setProbabilityEstimates(true);

			SelectedTag stag = new SelectedTag(LibSVM.SVMTYPE_EPSILON_SVR,
					LibSVM.TAGS_SVMTYPE);
			svm.setSVMType(stag);
			SelectedTag ktag = new SelectedTag(LibSVM.KERNELTYPE_SIGMOID,
					LibSVM.TAGS_KERNELTYPE);
			svm.setKernelType(ktag);

			return (Classifier) svm;

		} else {
			try {
				Classifier classifier = (Classifier) Class.forName("weka.classifiers.functions." + classfierName).newInstance();
				if(classifier instanceof SVMreg){
					((SVMreg) classifier).setKernel(new NormalizedPolyKernel());
				}
				return classifier;
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return null;
	}
	
	Instances getTrainingSet(){
		FastVector fvNominalVal = new FastVector();
		Attribute docLenAtt = new Attribute("docLen");
		fvNominalVal.addElement(docLenAtt);
		Attribute mitraAtt = new Attribute("mitra");
		fvNominalVal.addElement(mitraAtt);

		// add class category feature
		/*
		 * FastVector fvClassVal = new FastVector(2);
		 * fvClassVal.addElement("positive"); fvClassVal.addElement("negative");
		 * Attribute ClassAttribute = new Attribute("theClass", fvClassVal);
		 * fvNominalVal.addElement(ClassAttribute);
		 */
		Attribute ClassAttribute = new Attribute("theClass");
		fvNominalVal.addElement(ClassAttribute);

		int FutureSize = fvNominalVal.size();
		Instances trainingSet = new Instances("Rel", fvNominalVal, FutureSize);
		return trainingSet;
	}
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.lucene.postProcess.PostProcess#postProcess(org.apache.lucene
	 * .search.TopDocCollector, org.apache.lucene.search.Searcher)
	 */
	public TopDocCollector postProcess(RBooleanQuery query,
			TopDocCollector topDoc, Searcher seacher) {
		setup(query, topDoc, seacher); // it is necessary
		output(topDoc);
		trecR.evaluate("");
		float optBeta = 0; 
		double map = trecR.AveragePrecision;
		for(float beta = 0.1f; beta < 1.1 ; beta += 0.1){
			QueryExpansionAdap qea = new QueryExpansionAdap();
			TopDocCollector tdc = qea.postProcess(query, topDoc, seacher);
			output(topDoc);
			trecR.evaluate("");
			if(map <trecR.AveragePrecision){
				optBeta = beta;
				map = trecR.AveragePrecision;
			}
		}
		
		Instance ist = makeInstance();
		addInstance(ist);
		if(topicId.equalsIgnoreCase("")){
			build_save();
		}
		
		return topDoc;

	}
	private void output(TopDocCollector topDoc) {
		// TODO Auto-generated method stub
		
	}
}
