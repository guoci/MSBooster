/*
 * This file is part of MSBooster.
 *
 * MSBooster is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * MSBooster is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with MSBooster. If not, see <https://www.gnu.org/licenses/>.
 */

package Features;

import org.apache.commons.lang3.ArrayUtils;
import umich.ms.fileio.exceptions.FileParsingException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.ExecutionException;

//TODO: which of these tools allows O abd U amino acids?
public class PinReader {
    String name; //used for resetting
    BufferedReader in;
    String[] header;
    private String[] row;

    int scanNumIdx;
    int labelIdx;
    int rankIdx;
    int specIdx;
    int pepIdx;
    int eScoreIdx;
    private boolean calcEvalue = false;

    MzmlReader mzml;
    int length;

    public PinReader(String pin) throws IOException {
        name = pin;
        in = new BufferedReader(new FileReader(name));
        String line = in.readLine();
        header = line.split("\t");

        //set column indices
        scanNumIdx = ArrayUtils.indexOf(header, "ScanNr");
        labelIdx = ArrayUtils.indexOf(header, "Label");
        rankIdx = ArrayUtils.indexOf(header, "rank");
        specIdx = ArrayUtils.indexOf(header, "SpecId");
        pepIdx = ArrayUtils.indexOf(header, "Peptide");
        if (Arrays.asList(header).contains("log10_evalue")) {
            eScoreIdx = ArrayUtils.indexOf(header, "log10_evalue"); //DDA
        } else {
            eScoreIdx = ArrayUtils.indexOf(header, "hyperscore"); //DIA
            calcEvalue = true;
            Constants.RTescoreCutoff = (float) Math.pow(10, Constants.RTescoreCutoff);
            Constants.IMescoreCutoff = (float) Math.pow(10, Constants.IMescoreCutoff);
        }

        getLength();
    }

    //reload from start
    public void reset() throws IOException {
        in = new BufferedReader(new FileReader(name));
        String line = in.readLine();
    }

    //get next row ready
    public boolean next() throws IOException {
        String line = in.readLine();
        if (line != null) {
            row = line.split("\t");
            return true;
        }
        //in.close();
        return false;
    }

    public void close() throws IOException {
        in.close();
    }

    public void getLength() throws IOException {
        while (next()) {
            length += 1;
        }
        reset();
    }

    public String[] getRow() {return row;}

    public PeptideFormatter getPep() {
        String[] periodSplit = row[specIdx].split("\\.");
        return new PeptideFormatter(row[pepIdx], periodSplit[periodSplit.length - 1].split("_")[0], "pin");
    }

    public int getTD() {return Math.max(0, Integer.parseInt(row[labelIdx]));} //just leave as -1?

    public int getScanNum() {return Integer.parseInt(row[scanNumIdx]);}

    public int getRank() {
        try {
            return Integer.parseInt(row[rankIdx]);
        } catch (Exception e) {
            String[] specIdxSplit = row[specIdx].split("_");
            return Integer.parseInt(specIdxSplit[specIdxSplit.length - 1]);
        }
    }

    //public String getEScore() {return String.valueOf(Math.pow(10, Double.parseDouble(row[eScoreIdx])));}
    public String getEScore() {
        if (calcEvalue) {
            return String.valueOf(Math.exp(15.0 - Double.parseDouble(row[eScoreIdx])));
        } else {
            return row[eScoreIdx];
        }
    }

    public String[] createPDeep2List() throws IOException {
        ArrayList<String> peps = new ArrayList<String>();
        while (next()) {
            PeptideFormatter pf = getPep();
            peps.add(pf.stripped + "\t" + pf.mods + "\t" + pf.charge);
        }
        return peps.toArray(new String[0]);
    }

    public String[] createPDeep3List() throws IOException {
        ArrayList<String> peps = new ArrayList<String>();
        while (next()) {
            PeptideFormatter pf = getPep();
            peps.add("." + "\t" + "." + "\t" + pf.stripped + "\t" + pf.mods + "\t" + pf.charge);
        }
        return peps.toArray(new String[0]);
    }

    public String[] createDeepMSPeptideList() throws IOException {
        ArrayList<String> peps = new ArrayList<String>();
        while (next()) {
            peps.add(getPep().stripped);
        }
        return peps.toArray(new String[0]);
    }

    public String[] createDiannList() throws IOException {
        ArrayList<String> peps = new ArrayList<String>();
        //TreeMap<Integer, Integer> modMap = new TreeMap<>(); //sorted for future use
        while (next()) {
            PeptideFormatter pf = getPep();
            peps.add(pf.diann + "\t" + pf.charge);
        }
        return peps.toArray(new String[0]);
    }

    public String[] createPredFullList(File mzmlFile) throws IOException, InterruptedException, ExecutionException, FileParsingException {
        ArrayList<String> peps = new ArrayList<String>();
        if (Constants.NCE.equals("")) {
            mzml = new MzmlReader(mzmlFile.getCanonicalPath());
        }
        while (next()) {
            PeptideFormatter pf = getPep();
            if (! pf.stripped.contains("O") && ! pf.stripped.contains("U") &&
                    ! pf.stripped.contains("Z") && ! pf.stripped.contains("B") &&
                    ! pf.stripped.contains("X")) {
                String NCE = getNCE();
                peps.add(pf.predfull + "\t" + pf.charge + "\t" + Constants.FragmentationType + "\t" + NCE);
            }
        }
        return peps.toArray(new String[0]);
    }

    public String[] createPrositList(File mzmlFile) throws IOException, InterruptedException, ExecutionException, FileParsingException {
        ArrayList<String> peps = new ArrayList<String>();
        if (Constants.NCE.equals("")) {
            mzml = new MzmlReader(mzmlFile.getCanonicalPath());
        }
        while (next()) {
            PeptideFormatter pf = getPep();
            String NCE = getNCE();
            peps.add(pf.prosit + "," + NCE + "," + pf.charge);
        }
        return peps.toArray(new String[0]);
    }

    public String[] createPrositTMTList(File mzmlFile) throws IOException, InterruptedException, ExecutionException, FileParsingException {
        ArrayList<String> peps = new ArrayList<String>();
        if (Constants.NCE.equals("")) {
            mzml = new MzmlReader(mzmlFile.getCanonicalPath());
        }
        while (next()) {
            PeptideFormatter pf = getPep();
            String NCE = getNCE();
            peps.add(pf.prosit + "," + NCE + "," + pf.charge + "," + Constants.FragmentationType);
        }
        return peps.toArray(new String[0]);
    }

    public String[] createAlphapeptdeepList(File mzmlFile) throws IOException, InterruptedException, ExecutionException, FileParsingException {
        ArrayList<String> peps = new ArrayList<String>();
        if (Constants.NCE.equals("")) {
            mzml = new MzmlReader(mzmlFile.getCanonicalPath());
        }
        if (Constants.instrument.equals("")) {
            Constants.instrument = getInstrument();
        }
        while (next()) {
            PeptideFormatter pf = getPep();
            String NCE = getNCE();
            peps.add(pf.stripped + "," + pf.alphapeptdeepMods + "," + pf.modPositions + "," + pf.charge + "," +
                    NCE + "," + Constants.instrument + "," + pf.base);
        }
        return peps.toArray(new String[0]);
    }

    public String[] createFull() throws IOException {
        ArrayList<String> peps = new ArrayList<String>();
        //TreeMap<Integer, Integer> modMap = new TreeMap<>(); //sorted for future use
        while (next()) {
            PeptideFormatter pf = getPep();
            peps.add(pf.base + "\t" + pf.charge);
        }
        return peps.toArray(new String[0]);
    }

    private String getNCE() {
        if (Constants.NCE.equals("")) {
            return String.valueOf(mzml.scanNumberObjects.get(getScanNum()).NCEs.get(Constants.FragmentationType));
        } else {
            return Constants.NCE;
        }

    }

    private String getInstrument() {
        HashSet<String> LumosKeys = new HashSet<>(Arrays.asList("LTQ", "Lumos", "Fusion", "Elite", "Velos", "Eclipse", "Tribrid"));
        HashSet<String> QEKeys = new HashSet<>(Arrays.asList("QE", "Exactive", "Exploris"));
        HashSet<String> SciexTOFKeys = new HashSet<>(Arrays.asList("Sciex", "TripleTOF"));
        HashSet<String> timsTOFKeys = new HashSet<>(Arrays.asList("flight"));

        if (Constants.instrument.equals("")) {
            String model = mzml.scans.getRunInfo().getDefaultInstrument().getModel();
            String analyzer = mzml.scans.getRunInfo().getDefaultInstrument().getAnalyzer();
            for (String k : LumosKeys) {
                if (model.contains(k) || analyzer.contains(k)) {
                    return "Lumos";
                }
            }
            for (String k : QEKeys) {
                if (model.contains(k) || analyzer.contains(k)) {
                    return "QE";
                }
            }
            for (String k : SciexTOFKeys) {
                if (model.contains(k) || analyzer.contains(k)) {
                    return "SciexTOF";
                }
            }
            for (String k : timsTOFKeys) {
                if (model.contains(k) || analyzer.contains(k)) {
                    return "timsTOF";
                }
            }
            System.out.println("Could not detect instrument type. Setting to Lumos");
            return "Lumos"; //default if nothing found
        } else {
            return Constants.instrument;
        }
    }
}