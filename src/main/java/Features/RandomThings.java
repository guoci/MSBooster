package Features;

import umich.ms.fileio.exceptions.FileParsingException;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

public class RandomThings {

    public RandomThings(){

    }

    public static void main(String[] args) throws IOException, FileParsingException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        float sd = (float) Math.sqrt(StatMethods.variance(new double[] {1}));
        System.out.println(Float.isNaN(sd));
    }

    //this is for creating tsv for creating histograms for scores between target and decoy in python
//    public static void main(String[] args) throws FileParsingException, IOException {
//        String[] allSpectralAngles = new String[0];
//
//        //read in specific files
//        mgfFileReader predictedSpectra = new mgfFileReader("C:/Users/kevin/Downloads/proteomics/wideWindowPreds.mgf");
//        HashMap<String, double[]> predictedMZs = predictedSpectra.getMzDict();
//        HashMap<String, double[]> predictedIntensities = predictedSpectra.getMzDict();
//
//        String mzmlPath = "C:/Users/kevin/Downloads/proteomics/wideWindow/23aug2017_hela_serum_timecourse_pool_wide_002.mzML";
//        //load experimental spectra from mzML
//        mzMLReader mzMLscans = new mzMLReader(mzmlPath);
//
//        //get mzFreq
//        double[] mzFreqs = mzMLscans.getMzFreq();
//
//        File pepXMLDirectory = new File("C:/Users/kevin/Downloads/proteomics/wideWindow/");
//        String[] pepXMLfiles = pepXMLDirectory.list((d, name) -> name.endsWith(".pepXML")
//                && name.startsWith("23aug2017_hela_serum_timecourse_pool_wide_002")); //not recursive
//
//        for (String xmlPath : pepXMLfiles) {
//            System.out.println(xmlPath);
//            pepXMLReader xmlReader = new pepXMLReader("C:/Users/kevin/Downloads/proteomics/wideWindow/" + xmlPath);
//
//            //get all scan numbers from pepXML
//            int[] scanNums = xmlReader.getScanNumbers();
//
//            //get peptide names compatible with predicted HashMaps
//            String[] xmlPeptides = xmlReader.getXMLpeptides();
//
//            //get target or decoys
//            int[] td = xmlReader.getTargetOrDecoy();
//
//            //get expectation scores
//            String[] eScores = xmlReader.getEScore();
//
//            //iterate through all pepXMLhits
//            int pepXMLhits = scanNums.length;
//
//            //save spectral angles
//            String[] sa = new String[pepXMLhits];
//
//            for (int i = 0; i < pepXMLhits; i++) {
//                //get scanNumber and peptide of current hit
//                int num = scanNums[i];
//                String pep = xmlPeptides[i];
//
//                //get experimental spectra into
//                double[] expMZs = mzMLscans.getMZ(num);
//                double[] expIntensities = mzMLscans.getIntensity(num);
//
//                //get predicted spectra info
//                double[] predMZs = predictedMZs.get(pep);
//                double[] predIntensities = predictedIntensities.get(pep);
//
//                //calculate spectral angle
//                try {
//                    spectrumComparison specAngle = new spectrumComparison(expMZs, expIntensities,
//                            predMZs, predIntensities);
//
//                    //adapt for weighted or multiple sims, refer to pepXMLModifier
//                    HashMap<String, Double> sims = specAngle.getAllSimilarities(mzFreqs);
//
//                    //get expectation score and target or decoy
//                    String eScore = eScores[i];
//                    int tdSingle = td[i];
//
//                    sa[i] = pep + "\t" + sims.get("cosine") + "\t" + sims.get("weightCosine") + "\t" +
//                            sims.get("contrast") + "\t" + sims.get("weightContrast") + "\t" +
//                            sims.get("euclidean") + "\t" + sims.get("weightEuclidean") + "\t" +
//                            sims.get("bray-curtis") + "\t" + sims.get("weightBray-curtis") + "\t" +
//                            sims.get("pearson") + "\t" + sims.get("weightPearson") + "\t" +
//                            sims.get("dot") + "\t" + sims.get("weightDot") + "\t" +
//                            eScore + "\t" + tdSingle;
//
//                } catch (Exception e) {
//                    System.out.println(pep); //probably was not supported by pDeep2 prediction (ex. amino acid U)
//                }
//
//            }
//            //add to full list of spectral angles of peptides
//            allSpectralAngles = (String[]) ArrayUtils.addAll(allSpectralAngles, sa);
//        }
//
//
//        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//        //will need to remove null
//        //send to file for analysis/data viz in python/R
//        try {
//            FileWriter myWriter = new FileWriter("C:/Users/kevin/Downloads/proteomics/wideWindow/sims.tsv");
//            //need to adapt for multiple similarities
//            myWriter.write("peptide" + "\t" + "cosineSimilarity" + "\t" + "weightedCosineSimilarity" + "\t" +
//                    "spectralContrastAngle" + "\t" + "weightedSpectralContrastAngle" + "\t" +
//                    "euclideanDistance" + "\t" + "weightedEuclideanDistance" + "\t" +
//                    "brayCurtis" + "\t" + "weightedBrayCurtis" + "\t" +
//                    "pearsonCorr" + "\t" + "weightedPearsonCorr" + "\t" +
//                    "dotProduct" + "\t" + "weightedDotProduct" + "\t" +
//                    "eScore" + "\t" + "target=1/decoy=0\n");
//
//            for (String s : allSpectralAngles) {
//                if (s != null) {
//                    myWriter.write(s + "\n");
//                } else {
//                    System.out.println("found empty line");
//                }
//            }
//
//            myWriter.close();
//            System.out.println("Successfully wrote to the file.");
//        } catch (IOException e) {
//            System.out.println("An error occurred.");
//            e.printStackTrace();
//        }
//    }

//    public static void RTbins(mzMLReader mzml, mgfFileReader preds) throws IOException {
//        //get max index
//        int maxKey = Collections.max(mzml.scanNumberObjects.keySet());
//        int numBins = (int) mzml.scanNumberObjects.get(maxKey).RT + 1;
//
//        //set up arrays for predRT, assuming minute long bins
//        ArrayList<Float>[][] predRTfloor = new ArrayList[2][numBins];
//        for (int row = 0; row < 2; row++) {
//            for (int col = 0; col < numBins; col++) {
//                predRTfloor[row][col] = new ArrayList<Float>();
//            }
//        }
//
//        ArrayList<Float>[][] predRTround = new ArrayList[2][numBins + 1];
//        for (int row = 0; row < 2; row++) {
//            for (int col = 0; col < numBins + 1; col++) {
//                predRTround[row][col] = new ArrayList<Float>();
//            }
//        }
//
//        //write experimental RT for python plotting 2d kde
//        FileWriter[] files = new FileWriter[2];
//        files[0] = new FileWriter("C:/Users/kevin/Downloads/proteomics/scanNumberRTdecoy.txt");
//        files[1] = new FileWriter("C:/Users/kevin/Downloads/proteomics/scanNumberRTtarget.txt");
//
//        //iterate through scanNumbers
//        for (int scanNum : mzml.scanNumberObjects.keySet()) {
//            mzmlScanNumber scanNumObj = mzml.getScanNumObject(scanNum);
//            float rt = scanNumObj.RT; //experimental RT for this scan
//            int floor = (int) rt;
//            int round = (int) Math.round(rt);
//
//            //iterate through PSMs
//            for (int i = 1; i < scanNumObj.peptideObjects.size() + 1; i++) {
//                peptideObj pep = scanNumObj.getPeptideObject(i);
//                //identify if target or decoy
//                int td = pep.targetORdecoy;
//
//                //testing out adding more info for decoys
//                if (td == 0) {
//                    predRTfloor[0][floor].add(pep.RT);
//                    predRTround[0][round].add(pep.RT);
//                    files[0].write(rt + "\n");
//                    continue;
//                }
//                //end this section
//
//                //decide instances to add based on eScore (subject to change)
//                int instances = -1 * (int) Math.ceil(Math.log10(Double.parseDouble(pep.escore)));
//                if (instances > 0) { //add directly to distribution now
//                    for (int j = 0; j < instances; j++) {
//                        predRTfloor[td][floor].add(pep.RT);
//                        predRTround[td][round].add(pep.RT);
//                        files[td].write(rt + "\n");
//                    }
//                }
//            }
//        }
//        files[0].close();
//        files[1].close();
//
//        //now we have our distributions
//        FileWriter w = new FileWriter("C:/Users/kevin/Downloads/proteomics/empDistDecoy.txt");
//        for (ArrayList a : predRTfloor[0]) {
//            for (Object d : a) {
//                w.write(d + "\t");
//            }
//            w.write("\n");
//        }
//        w.close();
//
//
//    }

//for creating diannFrags and pDeep3Frags for comparing number fragments nad similarity predictions (predictionCompare.ipynb)
//SpectralPredictionMapper spm = SpectralPredictionMapper.createSpectralPredictionMapper
//        ("C:/Users/kevin/OneDriveUmich/proteomics/preds/cptacDiann.predicted.bin");
//    //        SpectralPredictionMapper spm = new mgfFileReader("C:/Users/kevin/OneDriveUmich/proteomics/preds/cptacPreds.mgf");
//    FileWriter myWriter = new FileWriter("C:/Users/kevin/Downloads/proteomics/cptac/diannFrags.tsv");
//    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
//    mzMLReader mzml = new mzMLReader("C:/Users/kevin/OneDriveUmich/proteomics/mzml/cptac/" +
//            "CPTAC_CCRCC_W_JHU_LUMOS_C3L-01665_T.mzML");
//
//        for (int i = 1; i < 17; i++) {
//        pepXMLReader pepxml = new pepXMLReader("C:/Users/kevin/Downloads/proteomics/cptac/2021-2-21/" +
//                "CPTAC_CCRCC_W_JHU_LUMOS_C3L-01665_T_rank" + i + ".pepXML");
//        mzml.setPepxmlEntries(pepxml, i, spm);
//    }
//
//        for (int i : mzml.scanNumberObjects.keySet()) {
//        mzmlScanNumber msn = mzml.getScanNumObject(i);
//
//        for (peptideObj pObj : msn.peptideObjects) {
//            int numPred = pObj.spectralSimObj.predMZs.length;
//            int numMatch = 0;
//            for (float f : pObj.spectralSimObj.matchedIntensities) {
//                if (f != 0f) {
//                    numMatch += 1;
//                }
//            }
//            myWriter.write(numPred + "\t" + numMatch + "\t" + pObj.targetORdecoy + "\t" +
//                    pObj.spectralSimObj.brayCurtis() + "\n");
//        }
//    }
//        myWriter.close();
}
