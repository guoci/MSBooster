package Features;

import org.apache.commons.lang3.ArrayUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

//TODO: which of these tools allows O abd U amino acids?
public class pinReader {
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

    public pinReader(String pin) throws IOException {
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
        }
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

    public String[] getRow() {return row;}

    public PeptideFormatter getPep() {
        String[] periodSplit = row[specIdx].split("\\.");
        return new PeptideFormatter(row[pepIdx], periodSplit[periodSplit.length - 1].split("_")[0], "pin");
    }

    public int getTD() {return Math.max(0, Integer.parseInt(row[labelIdx]));}

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

    public String[] createPDeep3List() throws IOException {
        ArrayList<String> peps = new ArrayList<String>();
        while (next()) {
            String[] pepSplit = getPep().baseCharge.split("\\|");
            peps.add("." + "\t" + "." + "\t" + pepSplit[0] + "\t" + pepSplit[1] + "\t" + pepSplit[2]);
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

    public String[] createPredFullList() throws IOException {
        ArrayList<String> peps = new ArrayList<String>();
        while (next()) {
            PeptideFormatter pf = getPep();
            if (! pf.predfull.contains("O") && ! pf.predfull.contains("U") &&
                    ! pf.predfull.contains("Z") && ! pf.predfull.contains("B") &&
                    ! pf.predfull.contains("X")) {
                peps.add(pf.predfull + "\t" + pf.charge + "\t" + Constants.FragmentationType + "\t" + Constants.NCE);
            }
        }
        return peps.toArray(new String[0]);
    }

    public String[] createPrositList() throws IOException {
        ArrayList<String> peps = new ArrayList<String>();
        while (next()) {
            PeptideFormatter pf = getPep();
            peps.add(pf.prosit + "," + Constants.NCE + "," + pf.charge);
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

    public static void main(String[] args) throws IOException {
        pinReader p = new pinReader("C:/Users/kevin/Downloads/proteomics/melanoma/201905024_F_7951_pro_1.pin");
        p.next();
        System.out.println(p.getEScore());
    }
}