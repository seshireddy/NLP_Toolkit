package processing;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import utils.DataManager;
import utils.IOOperator;
import utils.ParameterSetting;
import utils.TextUtil;
import utils.Logger;

import data_structure.Extraction;
import data_structure.SentenceEntry;
import data_structure.Template;

public class Extraction_bootstrapping {
	public static Extraction_bootstrapping singleton;
	public static Extraction_bootstrapping getInstance() {
		if (singleton == null)
			singleton = new Extraction_bootstrapping();
		return singleton;
	}
	
	private Map<Template, Integer> templateMap;
	private Map<Extraction, ArrayList<Integer>> extractionMap;
	private Map<String, Integer> attrList;
	private Map<String, Integer> valList;
	//private ArrayList<String> attrList;  
	//private ArrayList<String> valList;
	
	private int bootstrapping_cutoff = ParameterSetting.BOOTSTRAPPINGTHRESHOLD;
	public ArrayList<SentenceEntry> corpus;
	
	int iterationIndex = 0;

	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		DataManager.getInstance();
		// TODO Auto-generated method stub
		for(File input : DataManager.getFilesUnderFolder(ParameterSetting.PATHTOCRAWLEDDATA3)){
			if(input.getName().split("_").length != 3)
				continue;
			long startTime = System.currentTimeMillis();
			ArrayList<String> tmp =  DataManager.getInstance().getSentencesInFile(input);
			int size = tmp.size();
			Extraction_bootstrapping.getInstance().UpdateCorpus(tmp, input.getName());
			long ellapse = System.currentTimeMillis() - startTime;
			//System.out.println(input.getName() + " \t " + size + " Execution time: " + ellapse);

			//break;
		}
		System.out.println(Extraction_bootstrapping.getInstance().corpus.get(414663).UniqueID+"\t" + Extraction_bootstrapping.getInstance().corpus.get(414663).get_senttxt());
		Extraction_bootstrapping.getInstance().StartProcess();
		Extraction_bootstrapping.getInstance().WriteResulttoFile();
		System.out.println("long wait.....it's finished!....");
	}
	
	public Extraction_bootstrapping(){
		extractionMap = new HashMap<Extraction, ArrayList<Integer>>();
		templateMap = new HashMap<Template, Integer>();
		corpus = new ArrayList<SentenceEntry>();
		attrList = new HashMap<String, Integer>();
		valList = new HashMap<String, Integer>();
		
		InitSeedExtraction();
	}
	
	//Init an Extraction Map which would be used in the first Template Induction.
	//Init the ValueList & AttrList would be used in the first Value Induction & Attribute Induction.
	public void InitSeedExtraction(){
		try {
			BufferedReader seedReader = new BufferedReader(new FileReader(ParameterSetting.PATHTOSEEDFILE));
			String line = "";
			while((line = seedReader.readLine()) != null){
				String[] res = line.split("\t");
				if(res.length != (ParameterSetting.MAXSEEDSADJ + 1))
					continue;
				for(int i=1; i<ParameterSetting.MAXSEEDSADJ; i++){
					extractionMap.put(new Extraction(res[i], res[0], 0), new ArrayList<Integer>());
					if(!valList.containsKey(res[i])){
						valList.put(res[i], 0);
					}
				}
				if(!attrList.containsKey(res[0])){
					attrList.put(res[0], 0);
				}
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.err.println("Init seed dict.....  Size : " + extractionMap.size());
	}
	int i = 0;
	public void UpdateCorpus(ArrayList<String> sents, String filename){
		for(String sent: sents){
			String tmp = TextUtil.TextPreProcessing(sent);
			corpus.add(new SentenceEntry(tmp, i, filename));
			i++;
		}
		System.out.println("Corpus updated at size: "  + corpus.size()  + "\t" + i + "\t" + filename);
	}
	
	public void StartProcess(){
		int lastIterationSize = -1;
		int lastAttrListSize = -1;
		int lastValListSize = -1;
		//while(extractionMap.size() != lastIterationSize || attrList.size() !=  lastAttrListSize || valList.size() != lastValListSize){
		while(Math.abs(extractionMap.size() - lastIterationSize) > 5 || Math.abs(attrList.size() - lastAttrListSize) > 5 || Math.abs(valList.size() - lastValListSize) > 5 ){
				
			System.out.println(iterationIndex + "th iteration......" );
			lastIterationSize = extractionMap.size();
			lastAttrListSize = attrList.size();
			lastValListSize = valList.size();
			Logger.getInstance().getElapseTime(true);
			TemplateInduction();
			Logger.getInstance().getElapseTime(true);
			AttributeInduction();
			Logger.getInstance().getElapseTime(true);
			ValueInduction();
			Logger.getInstance().getElapseTime(true);
			UpdateToNewIteration();
			OutputProcessingRes();
			iterationIndex++;			
		}
		return;
	}
	
	public void ValueInduction(){
		int i = 0;
		int presize = extractionMap.size();
		Map<Extraction,  ArrayList<Integer>> cacheMap = new HashMap<Extraction,  ArrayList<Integer>>();
		long progressReportor = 0;
		for(SentenceEntry sentEntry: corpus){
			progressReportor++;
			if(progressReportor % 10000 == 0)
				Logger.getInstance().reportProcess(progressReportor, corpus.size(), "Value Induction");
			
			String sent = sentEntry.get_senttxt();
			ArrayList<Extraction> extractionsInSent = new ArrayList<Extraction>();
			for(Template pattern: sentEntry.CandidateTemplates){
				ArrayList<String> tmpset = sentEntry.CandidateAttribute.size() == 0? new ArrayList(attrList.keySet()): sentEntry.CandidateAttribute;
				
				for(String attr : tmpset){
					String value = pattern.getValueExtraction(sent, attr);
					if(value != null){
						Extraction curExtraction = new Extraction(value, attr, 0);
						if(!extractionsInSent.contains(curExtraction)){
							extractionsInSent.add(curExtraction);
						}
					}
				}
			}

			for(Extraction extraction : extractionsInSent){
				i++;
				if(extractionMap.containsKey(extraction)){
					if(!extractionMap.get(extraction).contains(sentEntry.UniqueID)){
						if(corpus.get(sentEntry.UniqueID).get_senttxt().contains(extraction.getAttr().get_txt()) 
								&& corpus.get(sentEntry.UniqueID).get_senttxt().contains(extraction.getVal().get_txt()))
							extractionMap.get(extraction).add(sentEntry.UniqueID);
						else{
							System.out.println(extraction.toString() + "\t" +sentEntry.get_senttxt());
						}
					}
					continue;
				}
				if(cacheMap.containsKey(extraction)){
					cacheMap.get(extraction).add(sentEntry.UniqueID);
				}
				else{
					cacheMap.put(extraction, new ArrayList<Integer>());
					cacheMap.get(extraction).add(sentEntry.UniqueID);
				}
			}
		}
		
		for(Iterator<Map.Entry<Extraction, ArrayList<Integer>>> it = cacheMap.entrySet().iterator(); it.hasNext();){
			Map.Entry<Extraction,ArrayList<Integer>> e = it.next();
			if(e.getValue().size() < bootstrapping_cutoff)
				it.remove();
		}
		
		for(Map.Entry<Extraction, ArrayList<Integer>> entry : cacheMap.entrySet()){
			if(extractionMap.containsKey(entry.getKey())){
				extractionMap.get(entry.getKey()).addAll(entry.getValue());
			}
			else{
				extractionMap.put(entry.getKey(), new ArrayList<Integer>());
				extractionMap.get(entry.getKey()).addAll(entry.getValue());
			}
		}
		System.out.println( i+ " value based exttractions were found. " + (extractionMap.size() - presize) + " new exttractions were added. " + extractionMap.size() + " extractions in total. ");
		return;
	}
		
	public void AttributeInduction(){
		int i = 0;
		int presize = extractionMap.size();
		Map<Extraction, ArrayList<Integer>> cacheMap = new HashMap<Extraction, ArrayList<Integer>>();
		
		long progressReportor = 0;
		for(SentenceEntry sentEntry: corpus){
			progressReportor++;
			if(progressReportor % 10000 == 0)
				Logger.getInstance().reportProcess(progressReportor, corpus.size(), "Attribute Induction");
			
			String sent = sentEntry.get_senttxt();
			ArrayList<Extraction> extractionsInSent = new ArrayList<Extraction>();
			for(Template pattern: sentEntry.CandidateTemplates){
				ArrayList<String> tmpset = sentEntry.CandidateValues.size() == 0? new ArrayList(valList.keySet()) : sentEntry.CandidateValues;
				
				for(String val : tmpset){
					String attribute = pattern.getAttrExtraction(sent, val);		
					if(attribute != null){
						Extraction curExtraction = new Extraction(val, attribute, 0);
						if(!extractionsInSent.contains(curExtraction)){
							extractionsInSent.add(curExtraction);
						}
					}
				}
			}
			for(Extraction extraction : extractionsInSent){
				i++;
				if(extractionMap.containsKey(extraction)){
					if(!extractionMap.get(extraction).contains(sentEntry.UniqueID)){
						if(corpus.get(sentEntry.UniqueID).get_senttxt().contains(extraction.getAttr().get_txt()) 
								&& corpus.get(sentEntry.UniqueID).get_senttxt().contains(extraction.getVal().get_txt()))
							extractionMap.get(extraction).add(sentEntry.UniqueID);
						else{
							extractionMap.get(extraction).add(sentEntry.UniqueID);
						}
					}
					continue;
				}

				if(cacheMap.containsKey(extraction)){
					cacheMap.get(extraction).add(sentEntry.UniqueID);
				}
				else{
					cacheMap.put(extraction, new ArrayList<Integer>());
					cacheMap.get(extraction).add(sentEntry.UniqueID);
				}
			}
		}
		for(Iterator<Map.Entry<Extraction,ArrayList<Integer>>> it = cacheMap.entrySet().iterator(); it.hasNext();){
			Map.Entry<Extraction,ArrayList<Integer>> e = it.next();
			if(e.getValue().size() < bootstrapping_cutoff)
				it.remove();
		}
		
		for(Map.Entry<Extraction, ArrayList<Integer>> entry : cacheMap.entrySet()){
			if(extractionMap.containsKey(entry.getKey())){
				extractionMap.get(entry.getKey()).addAll(entry.getValue());
			}
			else{
				extractionMap.put(entry.getKey(), new ArrayList<Integer>());
				extractionMap.get(entry.getKey()).addAll(entry.getValue());
			}
		}
		
		System.out.println( i+ " attribute based exttractions were found. " + (extractionMap.size() - presize) + " new exttractions were added. " + extractionMap.size() + " extractions in total. ");
		return;
	}
	
	//Extract the template based on the extraction already have.
	public void TemplateInduction(){
		int i = 0;
		int presize = templateMap.size();
		Map<Template, Integer> cacheMap = new HashMap<Template, Integer>();
		 /* sortingMap is used to debug the template extractions.
		 HashMapValueComparator sortingcomp = new HashMapValueComparator(templateMap);
		 TreeMap sortingMap = new TreeMap(sortingcomp);
		*/
		for(SentenceEntry sentEntry: corpus){
			String sent = sentEntry.get_senttxt();
			for(Extraction curExtract: extractionMap.keySet()){
				Template pattern = TextUtil.patternExtraction(curExtract.getVal(), curExtract.getAttr(), sent);
				if(pattern != null){
					if(cacheMap.containsKey(pattern)){
						cacheMap.put(pattern, cacheMap.get(pattern) + 1);
					}else{
						cacheMap.put(pattern, 1);
					}
					i++;
				}
			}
		}
		for(Iterator<Map.Entry<Template,Integer>> it = cacheMap.entrySet().iterator(); it.hasNext();){
			Map.Entry<Template,Integer> e = it.next();
			if(e.getValue() < bootstrapping_cutoff)
				it.remove();
		}
		for(Map.Entry<Template, Integer> entry : cacheMap.entrySet()){
			//IOOperator.getInstance().writeToFile(iterationIndex + ".txt", entry.getKey().toTemplateString() + "\t" + entry.getValue() + "\n", true);
			//assign new templates to different sentences. this could avoid lots of repeated computation.
			for(SentenceEntry sentEntry: corpus){
				if(entry.getKey().preQualify(sentEntry.get_senttxt()))
				{
					sentEntry.CandidateTemplates.add(entry.getKey());
				}
			}
			
			if(templateMap.containsKey(entry.getKey()))
				templateMap.put(entry.getKey(), entry.getValue() + templateMap.get(entry.getKey()));
			else
				templateMap.put(entry.getKey(), entry.getValue());
		}
		//sortingMap.putAll(templateMap);
		System.out.println( i+ " templates were found. " + (templateMap.size()-presize) + " templates added. " + templateMap.size() + " templates in total. ");
		return;
	}
	
	//update Attribute List & Value List.
	//update bootstrapping_cutoff
	public void UpdateToNewIteration(){
		attrList.clear();
		valList.clear();
		for(Iterator<Map.Entry<Extraction, ArrayList<Integer>>> it = extractionMap.entrySet().iterator(); it.hasNext();){
			Map.Entry<Extraction, ArrayList<Integer>> e = it.next();
			if(e.getValue().size() < ParameterSetting.BOOTSTRAPPINGTHRESHOLD){
				it.remove();
				continue;
			}
			if(e.getKey() != null){
				if(!attrList.containsKey(e.getKey().getAttr().get_txt())){
					attrList.put(e.getKey().getAttr().get_txt(), 0);
				}
				if(!valList.containsKey(e.getKey().getVal().get_txt())){
					valList.put(e.getKey().getVal().get_txt(), 0);
				}
			}
		}
		for(SentenceEntry entry:corpus){
			String words[] = TextUtil.SentenceToWords(entry.get_senttxt());
			for(String word : words){
				if(word == null || word.length() == 0)
					continue;
				if(attrList.containsKey(word)){
					entry.CandidateAttribute.add(word);
				}else if(valList.containsKey(word)){
					entry.CandidateValues.add(word);
				}
			}
		}
		bootstrapping_cutoff += ParameterSetting.BOOTSTRAPPINGTHRESHOLD;
		
		System.out.println( attrList.size() + " attributes in total. " + valList.size() + "values in total. "  + extractionMap.size() + " extractions in total. ");

		return;
	}
	
	private void OutputProcessingRes(){
		IOOperator.getInstance().writeToFile(iterationIndex + ".txt", "", false);
		IOOperator.getInstance().writeToFile(iterationIndex + ".txt", "============Overall============\n", true);
		IOOperator.getInstance().writeToFile(iterationIndex + ".txt", templateMap.size() + " templates in total. " + attrList.size() + " attributes in total. " + valList.size() + " values in total. "  + extractionMap.size() + " extractions in total. ", true);
		IOOperator.getInstance().writeToFile(iterationIndex + ".txt", "============Template============\n", true);
		
		for(Map.Entry<Template, Integer> entry : templateMap.entrySet()){
			//System.out.println(entry.getKey().toString());
			IOOperator.getInstance().writeToFile(iterationIndex + ".txt", entry.getKey().toTemplateString() + "\t" + entry.getValue() + "\n", true);
		}
		
		IOOperator.getInstance().writeToFile(iterationIndex + ".txt", "============Extraction============\n", true);
		
		for(Iterator<Map.Entry<Extraction, ArrayList<Integer>>> it = extractionMap.entrySet().iterator(); it.hasNext();){
			Map.Entry<Extraction, ArrayList<Integer>> entry = it.next();
			IOOperator.getInstance().writeToFile(iterationIndex + ".txt", entry.getKey().toString() + "\t" + entry.getValue() + "\n", true);
		}
	}
	
	public void WriteResulttoFile(){
		for(Iterator<Map.Entry<Extraction, ArrayList<Integer>>> it = extractionMap.entrySet().iterator(); it.hasNext();){
			Map.Entry<Extraction, ArrayList<Integer>> entry = it.next();
			for(Integer i : entry.getValue()){
				IOOperator.getInstance().writeToFile(ParameterSetting.PATHTOOUTPUT + "/" + corpus.get(i).FileName, entry.getKey().toString() + "\t" + i.toString() + "\n", true);
			}
			IOOperator.getInstance().writeToFile(iterationIndex + ".txt", entry.getKey().toString() + "\t" + entry.getValue() + "\n", true);
		}
	}
}

class HashMapValueComparator implements Comparator<Object> {
    Map theMapToSort;
    public HashMapValueComparator(Map theMapToSort) {
        this.theMapToSort = theMapToSort;
    }

    @Override
	public int compare(Object key1, Object key2) {
    	Integer val1 = (Integer)theMapToSort.get(key1);
    	Integer val2 = (Integer) theMapToSort.get(key2);
    	
        if (val1 < val2) {
            return 1;
        } else {
            return -1;
        }
    }
}
