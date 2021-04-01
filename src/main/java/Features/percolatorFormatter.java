package Features;

import Exceptions.UnsupportedInputException;
import com.univocity.parsers.tsv.TsvWriter;
import com.univocity.parsers.tsv.TsvWriterSettings;
import umich.ms.fileio.exceptions.FileParsingException;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class percolatorFormatter {

    public static String percolatorPepFormat(String[] columns, int pepIdx, int specIDidx) {
        String pep = columns[pepIdx];
        pep = pep.substring(2, pep.length() - 2);
        StringBuilder mods = new StringBuilder();

        //n term acetylation
        if (pep.charAt(0) == 'n') {
            pep = pep.replace("n[42.0106]", "");
            mods.append("0,Acetyl[AnyN-term];");
        }

        pep = pep.replace("C[57.0215]", "c");
        pep = pep.replace("M[15.9949]", "m");
        TreeMap<Integer, String> modsMap = new TreeMap<>();

        //carbamidometylation
        while (pep.contains("c")) {
            int pos = pep.indexOf("c") + 1;
            pep = pep.replaceFirst("c", "C");
            modsMap.put(pos, ",Carbamidomethyl[C];");
        }

        //methionine oxidation
        while (pep.contains("m")) {
            int pos = pep.indexOf("m") + 1;
            pep = pep.replaceFirst("m", "M");
            modsMap.put(pos, ",Oxidation[M];");
        }

        for (Map.Entry<Integer, String> entry : modsMap.entrySet()) {
            mods.append(entry.getKey()).append(entry.getValue());
        }

        //charge
        String[] charges = columns[specIDidx].split("_");
        String chargeStr = charges[charges.length - 2];
        int charge = Integer.parseInt(chargeStr.substring(chargeStr.length() - 1));

        return pep + "|" + mods + "|" + charge;
    }

    //set mgf or detectFile as null if not applicable
    //baseNames is the part before mzml or pin extensions
    public static void editPin(String[] baseNames, String pinDirectory, String mzmlDirectory, String mgf, String detectFile,
                               String[] features, String outfile)
            throws IOException, UnsupportedInputException {
        //check that all requested features are valid names
        for (String f : features) {
            if (! Constants.allowedFeatures.contains(f)) {
                throw new UnsupportedInputException("UnsupportedInputException", f + " is not an allowed feature. " +
                        "Please choose from the following: " + Constants.allowedFeatures);
            }
        }
        List<String> featuresList = Arrays.asList(features);

        //booleans for future determination of what to do
        boolean needsMGF = false;

        //get full path names for pin and mzml files
        String[] pinFiles = new String[baseNames.length];
        String[] mzmlFiles = new String[baseNames.length];
        for (int i = 0; i < baseNames.length; i++) {
            pinFiles[i] = pinDirectory + File.separator + baseNames[i] + ".pin";
            mzmlFiles[i] = mzmlDirectory + File.separator + baseNames[i] + ".mzML";
        }

        //load predicted spectra
        SpectralPredictionMapper predictedSpectra = null;

        //Special preparations dependent on features we require
        //only time this isn't needed is detect
        //could consider an mgf constant
        if (featuresList.contains("deltaRTlinear") || featuresList.contains("deltaRTbins") ||
                featuresList.contains("RTzscore") || featuresList.contains("RTprobability") ||
                featuresList.contains("RTprobabilityUnifPrior") || featuresList.contains("brayCurtis") ||
                featuresList.contains("cosineSimilarity") || featuresList.contains("spectralContrastAngle") ||
                featuresList.contains("euclideanDistance") || featuresList.contains("pearsonCorr") ||
                featuresList.contains("dotProduct") || featuresList.contains("deltaRTLOESS")) {
            System.out.println("loading predicted spectra");
            predictedSpectra = SpectralPredictionMapper.createSpectralPredictionMapper(mgf);
            needsMGF = true;
        }

        //detectability
        detectMap dm = null;
        if (detectFile != null) {
            dm = new detectMap(detectFile);
        }

        TsvWriter writer = new TsvWriter(new File(outfile), new TsvWriterSettings());
        try {
            //////////////////////////////iterate through pin and mzml files//////////////////////////////////////////
            for (int i = 0; i < baseNames.length; i++) {
                //load mzml file
                System.out.println("loading " + mzmlFiles[i]);
                mzMLReader mzml = new mzMLReader(mzmlFiles[i]);

                //load pin file, which already includes all ranks
                System.out.println("loading " + pinFiles[i]);
                pinReader pin = new pinReader(pinFiles[i]);

                //if first pin file, use it to add header to written tsv
                if (i == 0) {
                    ArrayList<String> newHeader = new ArrayList<>();
                    newHeader.addAll(Arrays.asList(pin.header));
                    newHeader.addAll(pin.pepIdx, featuresList); //add features before Peptide
                    writer.writeHeaders(newHeader);
                }

                //Special preparations dependent on features we require
                if (needsMGF) {
                    System.out.println("loading PSMs onto mzml object");
                    mzml.setPinEntries(pin, predictedSpectra);
                }
                if (featuresList.contains("deltaRTLOESS")) {
                    System.out.println("generating LOESS regression");
                    mzml.setLOESS(predictedSpectra, Constants.RTregressionSize, Constants.bandwidth, Constants.robustIters);
                }
                if (featuresList.contains("deltaRTlinear")) {
                    System.out.println("calculating delta RT linear");
                    if (mzml.expAndPredRTs != null) { mzml.setBetas();
                    } else { mzml.setBetas(predictedSpectra, Constants.RTregressionSize);
                    }
                    System.out.println(Arrays.toString(mzml.getBetas())); //print beta 0 and 1
                    mzml.normalizeRTs();
                }
                if (featuresList.contains("deltaRTbins") || featuresList.contains("RTzscore") ||
                        featuresList.contains("RTprobability") || featuresList.contains("RTprobabilityUnifPrior")) {
                    System.out.println("generating RT bins");
                    mzml.setRTbins(predictedSpectra);
                    mzml.propagateRTBinStats(); //may need to make more specific
                }
                int unifPriorSize = 0; //only used if using uniform prior
                float unifProb = 0;
                if (featuresList.contains("RTprobabilityUnifPrior")) {
                    //decide how many uniform points to add
                    int[] binSizes = new int[mzml.RTbins.length];
                    for (int bin = 0; bin < mzml.RTbins.length; bin++) {
                        binSizes[bin] = mzml.RTbins[bin].size();
                    }
                    Arrays.sort(binSizes);
                    int cutoff = (int) Math.floor(((double) mzml.RTbins.length / 100.0) * Constants.uniformPriorPercentile);
                    unifPriorSize = binSizes[cutoff];

                    //also need uniform probability with right bound of max predicted RT
                    unifProb = 1.0f / predictedSpectra.getMaxPredRT();
                }
                if (featuresList.contains("RTprobability") || featuresList.contains("RTprobabilityUnifPrior")) {
                    System.out.println("generating empirical densities");
                    mzml.setKernelDensities();
                }

                System.out.println("getting predictions for each row");
                while (pin.next()) {
                    //peptide name
                    String pep = pin.getPep();

                    //get entry
                    peptideObj pepObj = null;
                    if (needsMGF) {
                        pepObj = mzml.scanNumberObjects.get(pin.getScanNum()).getPeptideObject(pep);
                    }

                    //write everything we already have, not including extra protein columns
                    String[] row = pin.getRow();
                    for (int j = 0; j < pin.header.length; j++) {
                        writer.addValue(pin.header[j], row[j]);
                    }
                    //add extra protein columns
                    if (pin.getRow().length > pin.header.length) {
                        for (int j = pin.header.length; j < pin.getRow().length; j++) {
                            writer.addValue(j + features.length, row[j]);
                        }
                    }

                    //switch case
                    for (String feature : features) {
                        switch (feature) {
                            case "detectability":
                                writer.addValue("detectability", dm.getDetectability(pep));
                                break;
                            case "deltaRTlinear":
                                writer.addValue("deltaRTlinear", pepObj.deltaRT);
                                break;
                            case "deltaRTbins":
                                writer.addValue("deltaRTbins", pepObj.deltaRTbin);
                                break;
                            case "deltaRTLOESS":
                                writer.addValue("deltaRTLOESS",
                                        Math.abs(mzml.predictLOESS(pepObj.scanNumObj.RT) - pepObj.RT));
                                break;
                            case "RTzscore":
                                writer.addValue("RTzscore", pepObj.RTzscore);
                                break;
                            case "RTprobability":
                                writer.addValue("RTprobability", pepObj.RTprob);
                                break;
                            case "RTprobabilityUnifPrior":
                                float prob = RTFunctions.RTprobabilityWithUniformPrior(unifPriorSize, unifProb,
                                        pepObj.scanNumObj.RTbinSize, (float) pepObj.RTprob);
                                writer.addValue("RTprobabilityUnifPrior", prob);
                                break;
                            case "brayCurtis":
                                writer.addValue("brayCurtis", pepObj.spectralSimObj.brayCurtis());
                                break;
                            case "cosineSimilarity":
                                writer.addValue("cosineSimilarity", pepObj.spectralSimObj.cosineSimilarity());
                                break;
                            case "spectralContrastAngle":
                                writer.addValue("spectralContrastAngle", pepObj.spectralSimObj.spectralContrastAngle());
                                break;
                            case "euclideanDistance":
                                writer.addValue("euclideanDistance", pepObj.spectralSimObj.euclideanDistance());
                                break;
                            case "pearsonCorr":
                                writer.addValue("pearsonCorr", pepObj.spectralSimObj.pearsonCorr());
                                break;
                            case "dotProduct":
                                writer.addValue("dotProduct", pepObj.spectralSimObj.dotProduct());
                                break;
                        }
                    }
                    //flush values to output
                    writer.writeValuesToRow();
                }
                pin.close();
            }
            writer.close();
        } catch (IOException | FileParsingException e) {
            writer.close();
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException, UnsupportedInputException {
//        editPin(new String[] {"23aug2017_hela_serum_timecourse_pool_wide_001",
//                "23aug2017_hela_serum_timecourse_pool_wide_002",
//                "23aug2017_hela_serum_timecourse_pool_wide_003"},
//                "C:/Users/kevin/Downloads/proteomics/wide/",
//                "C:/Users/kevin/OneDriveUmich/proteomics/mzml/wideWindow/",
//                "C:/Users/kevin/OneDriveUmich/proteomics/preds/widePDeep3.mgf",
//                null,
//                new String[] {"brayCurtis", "euclideanDistance", "cosineSimilarity", "spectralContrastAngle",
//                        "pearsonCorr", "dotProduct", "deltaRTlinear", "deltaRTbins", "RTzscore", "RTprobability",
//                        "RTprobabilityUnifPrior"},
//                "C:/Users/kevin/Downloads/proteomics/wide/perc/all.pin");

//        editPin(new String[] {"23aug2017_hela_serum_timecourse_4mz_narrow_1",
//                        "23aug2017_hela_serum_timecourse_4mz_narrow_2",
//                        "23aug2017_hela_serum_timecourse_4mz_narrow_3",
//                        "23aug2017_hela_serum_timecourse_4mz_narrow_4",
//                        "23aug2017_hela_serum_timecourse_4mz_narrow_5",
//                        "23aug2017_hela_serum_timecourse_4mz_narrow_6"},
//                "C:/Users/kevin/Downloads/proteomics/narrow/",
//                "C:/Users/kevin/OneDriveUmich/proteomics/mzml/narrowWindow/",
//                "C:/Users/kevin/OneDriveUmich/proteomics/preds/narrowPDeep3.mgf",
//                null,
//                new String[] {"brayCurtis", "euclideanDistance", "cosineSimilarity", "spectralContrastAngle",
//                        "pearsonCorr", "dotProduct", "deltaRTlinear", "deltaRTbins", "RTzscore", "RTprobability",
//                        "RTprobabilityUnifPrior"},
//                "C:/Users/kevin/Downloads/proteomics/narrow/perc/all.pin");

        editPin(new String[] {"CPTAC_CCRCC_W_JHU_LUMOS_C3L-01665_T"},
                "C:/Users/kevin/Downloads/proteomics/cptac/2021-2-21/",
                "C:/Users/kevin/OneDriveUmich/proteomics/mzml/cptac/",
                "C:/Users/kevin/OneDriveUmich/proteomics/preds/cptacPreds.mgf",
                //"C:/Users/kevin/OneDriveUmich/proteomics/preds/cptacDetect_predictions.txt",
                null,
                new String[] {"brayCurtis", "euclideanDistance", "cosineSimilarity", "spectralContrastAngle",
                        "pearsonCorr", "dotProduct", "deltaRTlinear", "deltaRTbins", "RTzscore", "RTprobability",
                        "RTprobabilityUnifPrior"},
                "C:/Users/kevin/Downloads/proteomics/cptac/2021-2-21/perc/test.pin");
    }
}