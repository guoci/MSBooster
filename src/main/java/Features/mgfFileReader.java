package Features;

import org.apache.commons.lang3.ArrayUtils;
import umich.ms.fileio.exceptions.FileParsingException;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.*;

public class mgfFileReader implements SpectralPredictionMapper{
    //mgfFileReader can handle both single files and entire directories

    ArrayList<String> filenames;
    HashMap<String, PredictionEntry> allPreds = new HashMap<>();
    public ConcurrentHashMap<Integer, mzmlScanNumber> scanNumberObjects = new ConcurrentHashMap<>();
    private List<Future> futureList = new ArrayList<>(Constants.numThreads);

    //this version if loading pDeep3 predictions
    //TODO: are different constructors doing similar things?
    public mgfFileReader(String files) throws IOException {
        File predsDirectory = new File(files);
        String[] predsFiles = predsDirectory.list();
        filenames = new ArrayList<String>();

        if (predsFiles == null) { //if user provided a file, not a directory
            filenames.add(files);
        } else { //user provides directory
            for (String predsFile : predsFiles) {
                if (predsFile.contains(".mgf")) {
                    filenames.add(files + File.separator + predsFile);
                }
            }
        }

        String line;
        String title = null;
        //int charge;
        double pepmass;
        //double precursorIntensity;
        float RT = 0;
        float IM = 0;
        ArrayList<Float> intensities = new ArrayList<>();
        ArrayList<Float> mzs = new ArrayList<>();
        String[] lineSplit;
        float[] finalMzs;
        float[] finalIntensities;

        for (String fname : filenames) {
//            MgfReader reader = new MgfReader(new File(fname), PeakList.Precision.FLOAT);
//            reader.acceptUnsortedSpectra();
            BufferedReader reader = new BufferedReader(new FileReader(fname));
//
//            // hasNext() returns true if there is more spectrum to read
//            while (reader.hasNext()) {
            while ((line = reader.readLine()) != null) {
//
//                // next() returns the next spectrum or throws an IOException is something went wrong
//                MsnSpectrum spectrum = reader.next();
//
//                // do some stuff with your spectrum

                if (line.contains("=")) {
                    lineSplit = line.split("=");
                    switch (lineSplit[0]) {
                        case "TITLE":
                            title = lineSplit[1];
                            break;
                        case "CHARGE":
//                            charge = Integer.parseInt(lineSplit[1].replace("+", ""));
                            break;
                        case "PEPMASS":
//                            lineSplit = lineSplit[1].split(" ");
//                            pepmass = Double.parseDouble(lineSplit[0]);
//                            allMasses.put(title, pepmass);
                            break;
                        case "RTINSECONDS":
                            RT = Float.parseFloat(lineSplit[1]);
                            break;
                        case "1/K0":
                            IM = Float.parseFloat(lineSplit[1]);
                            break;
                    }
                } else {
                    lineSplit = line.split(" ");
                    try { //fragment ions
                        mzs.add(Float.parseFloat(lineSplit[0]));
                        intensities.add(Float.parseFloat(lineSplit[1]));
                    } catch (Exception e) {
                        if (lineSplit[0].equals("END")) {
                            //unload
                            //some fragments have zero intensity
                            ArrayList<Integer> zeroFrags = new ArrayList<>();
                            for (int i = 0; i < intensities.size(); i++) {
                                if (intensities.get(i) == 0.0) {
                                    zeroFrags.add(i);
                                }
                            }

                            if (zeroFrags.size() == 0) {
                                finalMzs = ArrayUtils.toPrimitive(mzs.toArray(new Float[0]), 0.0F);
                                finalIntensities = ArrayUtils.toPrimitive(intensities.toArray(new Float[0]), 0.0F);
                            } else { //some empty frags
                                float[] newIntensities = new float[intensities.size() - zeroFrags.size()];
                                float[] newMzs = new float[intensities.size() - zeroFrags.size()];

                                int j = 0;
                                int k = 0;
                                int exclude = zeroFrags.get(j);
                                for (int i = 0; i < intensities.size(); i++) {
                                    if (i == exclude) {
                                        j += 1;
                                        try {
                                            exclude = zeroFrags.get(j);
                                        } catch (Exception e1) {
                                            exclude = -1; //no more empty frags
                                        }
                                    } else {
                                        newIntensities[k] = intensities.get(i);
                                        newMzs[k] = mzs.get(i);
                                        k += 1;
                                    }
                                }
                                finalMzs = newMzs;
                                finalIntensities = newIntensities;
                            }

                            PredictionEntry newPred = new PredictionEntry();
                            newPred.setMzs(finalMzs);
                            newPred.setIntensities(finalIntensities);
                            newPred.setRT(RT);
                            newPred.setIM(IM);
                            allPreds.put(title, newPred);

                            //reset for next peptide/PSM
                            mzs.clear();
                            intensities.clear();
                        }
                    }
                }
            }
            reader.close();
        }
    }

    private String returnString(char endChar, byte[] myData, int startInt) {
        int addInt = 0;
        while (!(myData[startInt + addInt] == endChar)) {
            addInt++;
        }
        byte[] byteArray = Arrays.copyOfRange(myData, startInt, startInt + addInt);
        return new String(byteArray);
    }

    private int returnAdd(char endChar, byte[] myData, int startInt) {
        int addInt = 0;
        while (!(myData[startInt + addInt] == endChar)) {
            addInt++;
        }
        return addInt;
    }

    //this version for uncalibrated mgf acting as mzml
    public mgfFileReader(String file, boolean createScanNumObjects, ExecutorService executorService)
            throws IOException, FileParsingException, ExecutionException, InterruptedException {

        //load data
        File myFile = new File(file);
        byte[] data = new byte[(int) myFile.length()];
        DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(myFile), 1 << 24));
        in.read(data);
        in.close();

        //find where specific lines are
        int[] chunks = new int[Constants.numThreads + 1];
        chunks[0] = 11; //skip first BEGIN IONS
        chunks[Constants.numThreads] = data.length;
        int jump = data.length / Constants.numThreads;
        for (int l = 1; l < Constants.numThreads; l++) {
            int start = jump * l;
            while (true) {
                if (data[start] == '\n') {
                    if (data[start + 1] == 'B') {
                        chunks[l] = start + 12; //skip first BEGIN IONS
                        break;
                    }
                }
                start++;
            }
        }
        
        //parallelize
        for (int i = 0; i < Constants.numThreads; i++) {
            int finalI = i;
            futureList.add(executorService.submit(() -> {
                int scanNum = 0;
                float RT = 0;
                float IM = 0;
                ArrayList<Float> intensities = new ArrayList<>(12000);
                ArrayList<Float> mzs = new ArrayList<>(12000);
                int start = chunks[finalI];
                int end = chunks[finalI + 1];
                String line = "";

                while (start < end - 11) {
                    switch (data[start]) {
                        case 'T': //TITLE
                            start += 6;
                            //TODO: make below part a private method. 2 versions, 1 where no bytearray/string is made.
                            // accept \n or space.
                            // toAdd will be length of string
                            line = returnString('\n', data, start);

                            String[] dotSplit = line.split("\\.");
                            scanNum = Integer.parseInt(dotSplit[dotSplit.length - 2]);
                            start += line.length() + 1;
                            break;
                        case 'C': //CHARGE
                            start += 7;
                            start += returnAdd('\n', data, start) + 1;
                            break;
                        case 'P': //PEPMASS
                            start += 8;
                            start += returnAdd('\n', data, start) + 1;
                            break;
                        case 'R': //RTINSECONDS
                            start += 12;
                            line = returnString('\n', data, start);
                            RT = Float.parseFloat(line);
                            start += line.length() + 1;
                            break;
                        case 'E': // END IONS
                            start += 9;
                            //do create scanNumObj
                            float[] mzArray = new float[mzs.size()];
                            for (int h = 0; h < mzs.size(); h++) {
                                mzArray[h] = mzs.get(h);
                            }
                            float[] intArray = new float[intensities.size()];
                            for (int h = 0; h < intensities.size(); h++) {
                                intArray[h] = intensities.get(h);
                            }
                            try {
                                scanNumberObjects.put(scanNum, new mzmlScanNumber(scanNum, mzArray, intArray, RT, IM));
                            } catch (FileParsingException fileParsingException) {
                                fileParsingException.printStackTrace();
                            }

                            //reset for next peptide/PSM
                            mzs.clear();
                            intensities.clear();
                            break;
                        case 'B': // BEGIN IONS
                            start += 11;
                            break;
                        case '1': // 1/K0
                            if (data[start + 1] == '/') {
                                start += 5;
                                line = returnString('\n', data, start);
                                IM = Float.parseFloat(line);
                                start += line.length() + 1;
                                break;
                            } else {
                                line = returnString(' ', data, start);
                                mzs.add(Float.parseFloat(line));
                                start += line.length() + 1;

                                line = returnString('\n', data, start);
                                intensities.add(Float.parseFloat(line));
                                start += line.length() + 1;
                                break;
                            }
                        case '2': //if number, skip switch
                        case '3':
                        case '4':
                        case '5':
                        case '6':
                        case '7':
                        case '8':
                        case '9':
                        case '0':
                            line = returnString(' ', data, start);
                            mzs.add(Float.parseFloat(line));
                            start += line.length() + 1;

                            line = returnString('\n', data, start);
                            intensities.add(Float.parseFloat(line));
                            start += line.length() + 1;
                            break;
                    }
                }
            }));
        }
        for (Future future : futureList) {
            future.get();
        }
    }

    public HashMap<String, PredictionEntry> getPreds() { return allPreds; }

    public float getMaxPredRT() {
        float maxRT = 0f;
        for (PredictionEntry entry : allPreds.values()) {
            if (entry.RT > maxRT) {
                maxRT = entry.RT;
            }
        }
        return maxRT;
    }

    public void clear() {
        allPreds.clear();
        scanNumberObjects.clear();
        futureList.clear();
    }

    public static void main(String[] args) throws IOException, FileParsingException, ExecutionException, InterruptedException {
        //mgfFileReader mgf = new mgfFileReader("C:/Users/kevin/OneDriveUmich/proteomics/preds/cptacPreds.mgf");
        ExecutorService executorService = Executors.newFixedThreadPool(12);
        Constants.numThreads = 12;
        long startTime = System.nanoTime();
        mgfFileReader mgf = new mgfFileReader("C:/Users/yangkl/OneDriveUmich/proteomics/mzml/" +
                "20180819_TIMS2_12-2_AnBr_SA_200ng_HeLa_50cm_120min_100ms_11CT_1_A1_01_2767_uncalibrated.mgf", true,
                executorService);
        System.out.println(mgf.scanNumberObjects.size());
        System.out.println(mgf.scanNumberObjects.get(176229).RT);
        System.out.println(mgf.scanNumberObjects.containsKey(5));
        System.out.println(mgf.scanNumberObjects.get(5).RT);
        mzMLReader mzml = new mzMLReader(mgf);
        long endTime = System.nanoTime();
        long duration = (endTime - startTime);
        System.out.println("loading took " + duration / 1000000 +" milliseconds");

        mgf = new mgfFileReader("C:/Users/yangkl/OneDriveUmich/proteomics/mzml/" +
                "20180819_TIMS2_12-2_AnBr_SA_200ng_HeLa_50cm_120min_100ms_11CT_2_A1_01_2768.mgf", true,
                executorService);
        mzml = new mzMLReader(mgf);
        System.out.println("hi");

        mgf = new mgfFileReader("C:/Users/yangkl/OneDriveUmich/proteomics/mzml/" +
                "20180819_TIMS2_12-2_AnBr_SA_200ng_HeLa_50cm_120min_100ms_11CT_3_A1_01_2769.mgf", true,
                executorService);
        mzml = new mzMLReader(mgf);
        System.out.println("hi");

        mgf = new mgfFileReader("C:/Users/yangkl/OneDriveUmich/proteomics/mzml/" +
                "20180819_TIMS2_12-2_AnBr_SA_200ng_HeLa_50cm_120min_100ms_11CT_4_A1_01_2770.mgf", true,
                executorService);
        mzml = new mzMLReader(mgf);

        endTime = System.nanoTime();
        duration = (endTime - startTime);
        System.out.println("loading took " + duration / 1000000 +" milliseconds");
        executorService.shutdown();
    }
}
