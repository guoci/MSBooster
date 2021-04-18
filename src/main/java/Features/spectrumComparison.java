package Features;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import umich.ms.fileio.exceptions.FileParsingException;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

import static Features.floatUtils.floatToDouble;

public class spectrumComparison implements Serializable {
    float[] predMZs;
    float[] predIntensities;
    float[] matchedIntensities;
    float[] unitNormMatchedIntensities;
    float[] unitNormPredIntensities;
    ArrayList<Integer> matchedIdx = new ArrayList<Integer>();
    private static final long serialVersionUID = Constants.uid;

    public spectrumComparison(float[] eMZs, float[] eIntensities,
                              float[] pMZs, float[] pIntensities) {
        predMZs = pMZs;
        predIntensities = pIntensities;
        matchedIntensities = this.getMatchedIntensities(eMZs, eIntensities);
    }

    public spectrumComparison(float[] eMZs, float[] eIntensities,
                              float[] pMZs, float[] pIntensities,
                              boolean filter) {
        predMZs = pMZs;
        predIntensities = pIntensities;
        if (filter) {
            this.filterTopFragments();
        }
        matchedIntensities = this.getMatchedIntensities(eMZs, eIntensities);
    }

    //this version is used when shuffling intensities
    private spectrumComparison(spectrumComparison specAngle) {
        this.predMZs = specAngle.predMZs;
        this.predIntensities = specAngle.predMZs;
        this.matchedIntensities = specAngle.matchedIntensities;
        this.unitNormMatchedIntensities = specAngle.unitNormMatchedIntensities;
    }

    private spectrumComparison(spectrumComparison specAngle, boolean filter) {
        this.predMZs = specAngle.predMZs;
        this.predIntensities = specAngle.predMZs;
        if (filter) {
            this.filterTopFragments();
            this.matchedIntensities = specAngle.matchedIntensities;
            //don't include unitNorm, because they'll have to be recalculated
        } else {
            this.matchedIntensities = specAngle.matchedIntensities;
            this.unitNormMatchedIntensities = specAngle.unitNormMatchedIntensities;
        }

    }

    private void filterTopFragments() {
        //skip if shorter
        if (this.predMZs.length > Constants.topFragments) {
            ArrayList<Float> tmpInts = new ArrayList<>();
            for (float i : predIntensities) {
                tmpInts.add(i);
            }

            ArrayList<Float> tmpMZs = new ArrayList<>();
            for (float i : predMZs) {
                tmpMZs.add(i);
            }

            //go through and remove minimum one at a time
            while (tmpInts.size() > Constants.topFragments) {
                int index = tmpInts.indexOf(Collections.min(tmpInts));
                tmpInts.remove(index);
                tmpMZs.remove(index);
            }

            predIntensities = new float[tmpInts.size()];
            for (int i = 0; i < tmpInts.size(); i++) {
                predIntensities[i] = tmpInts.get(i);
            }

            predMZs = new float[tmpInts.size()];
            for (int i = 0; i < tmpInts.size(); i++) {
                predMZs[i] = tmpMZs.get(i);
            }
        }
    }

    private float[] getMatchedIntensities(float[] expMZs, float[] expIntensities) {
        int startPos = 0;
        int matchedNum = 0;
        float[] matchedInts = new float[predMZs.length];
        double ppm = Constants.ppmTolerance / 1000000;

        /* Get best peaks from experimental spectrum that match to predicted peaks.
           Same experimental peak may match to the multiple predicted peaks,
              if they're close enough and experimental peak is strong.
           Unmatched peaks assigned 0
         */
        for (double mz : predMZs) {
            //see if any experimental peaks in vicinity
            //double fragmentError = ppm * mz;
            double fragmentMin = mz * (1 - ppm);
            double fragmentMax = mz * (1 + ppm);

            float predInt = 0;
            int pastStart = 0;

            while (startPos + pastStart < expMZs.length) {
                double startMass = expMZs[startPos + pastStart];

                if (startMass < fragmentMin) { //yet to reach peak within fragment tolerance
                    startPos += 1;
                } else if (startMass <= fragmentMax) { //peak within fragment tolerance
                    //only for use when removing peaks from lower ranks
                    if (Constants.removeRankPeaks) {
                        matchedIdx.add(startPos + pastStart);
                    }

                    float potentialInt = expIntensities[startPos + pastStart];

                    if (potentialInt > predInt) { //new maximum intensity
                        predInt = potentialInt;
                    }
                    pastStart += 1;
                } else { //outside of fragment tolerance range again
                    break;
                }
            }

            matchedInts[matchedNum] = predInt;
            matchedNum += 1;
        }
        return matchedInts;
    }

    public double[] getWeights(double[] freqs) {
        //will need to use predMZs
        int maxIndex = freqs.length;

        double[] weights = new double[predMZs.length];
        for (int i = 0; i < predMZs.length; i++) {
            int binIndex = (int) Math.floor(predMZs[i] / Constants.binwidth);
            if (binIndex < maxIndex) {
                weights[i] = freqs[binIndex];
            } else { //detected too big a fragment
                weights[i] = 0; //arbitrary, just ignore
            }
        }

        return weights;
    }

    public static float[] subNormalize(float[] vector) { //change back to private
        //if we wish to normalize to unit vector
        double magnitude = 0;
        for (double i : vector) {
            magnitude += i * i;
        }
        magnitude =  Math.sqrt(magnitude);

        float[] newVector = new float[vector.length];

        if (magnitude != 0) { //fixes Bray curtis
            float mag = (float) magnitude;
            for (int i = 0; i < newVector.length; i++) {
                newVector[i] = vector[i] / mag;
            }
        }

        return newVector;
    }

    public void unitNormalize() { //only use after filtering
        unitNormPredIntensities = subNormalize(predIntensities);
        unitNormMatchedIntensities = subNormalize(matchedIntensities);
    }

    public void filterIntensities(double min) {

        ArrayList<Float> removableIntensities = new ArrayList<>();
        ArrayList<Float> removableMZs = new ArrayList<>();

        for (int i = 0; i < predIntensities.length; i++) {
            float intensity = predIntensities[i];
            float mz = predMZs[i];

            if (intensity < min) {
                removableIntensities.add(intensity);
                removableMZs.add(mz);
            }
        }

        for (int i = 0; i < removableIntensities.size(); i++) {
            predIntensities = ArrayUtils.removeElement(predIntensities, removableIntensities.get(i));
            predMZs = ArrayUtils.removeElement(predMZs, removableMZs.get(i));
        }
    }

    public double[] rankIntensities(double[] vector) { //need to adapt for unitNorm vectors too
        double[] ranks = new double[vector.length];

        double[] sortedVector = vector;
        Arrays.sort(sortedVector);

        for (int i = 0; i < sortedVector.length; i++) {
            ranks[i] = ArrayUtils.indexOf(sortedVector, vector[i]) + 1;
        }

        return ranks;
    }

    public double cosineSimilarity() {

        //numerator
        double num = 0;
        for (int i = 0; i < predMZs.length; i++) {
            num += predIntensities[i] * matchedIntensities[i];
        }

        //denominator
        double a = 0;
        double b = 0;
        for (int i = 0; i < predMZs.length; i++) {
            a += predIntensities[i] * predIntensities[i];
            b += matchedIntensities[i] * matchedIntensities[i];
        }
        double den = Math.sqrt(a * b);

        if (den == 0) { //fixes no matched peaks
            return 0;
        } else {
            return num / den;
        }
    }

    //https://stats.stackexchange.com/questions/384419/weighted-cosine-similarity
    public double weightedCosineSimilarity(double[] weights) {

        //numerator
        double num = 0;
        for (int i = 0; i < predMZs.length; i++) {
            num += predIntensities[i] * matchedIntensities[i] * weights[i];
        }

        //denominator
        double a = 0;
        double b = 0;
        for (int i = 0; i < predMZs.length; i++) {
            a += predIntensities[i] * predIntensities[i] * weights[i];
            b += matchedIntensities[i] * matchedIntensities[i] * weights[i];
        }
        double den = Math.sqrt(a * b);

        if (den == 0) {
            return 0;
        } else {
            return num / den;
        }
    }

    public double spectralContrastAngle() {
        double cosSim = this.cosineSimilarity();
        return 1 - (2 * Math.acos(cosSim) / Math.PI);
    }

    public double weightedSpectralContrastAngle(double[] weights) {
        double cosSim = this.weightedCosineSimilarity(weights);
        return 1 - (2 * Math.acos(cosSim) / Math.PI);
    }

    public double euclideanDistance() {
        if (unitNormPredIntensities == null) {
            this.unitNormalize();
        }

        //max distance between two points in the positive quadrant with unit vectors is sqrt(2)
        float floatSum = 0.0f;
        for (float f : unitNormMatchedIntensities){
            floatSum += f;
        }
        if (floatSum == 0.0f) {
            return 1 - Math.sqrt(2);
        } else {
            double numSum = 0;
            for (int i = 0; i < predMZs.length; i++) {
                double diff = unitNormPredIntensities[i] - unitNormMatchedIntensities[i];
                double square = diff * diff;
                numSum += square;
            }
            return 1 - Math.sqrt(numSum);
        }
    }

    public double weightedEuclideanDistance(double[] weights) {
        if (unitNormPredIntensities == null) {
            this.unitNormalize();
        }

        float floatSum = 0.0f;
        for (float f : unitNormMatchedIntensities){
            floatSum += f;
        }
        if (floatSum == 0.0f) {
            return 1 - Math.sqrt(2);
        } else {
            //now we need to normalize again by weights
            float[] newNormPred = new float[unitNormPredIntensities.length];
            float[] newNormMatched = new float[unitNormMatchedIntensities.length];
            for (int i = 0; i < unitNormPredIntensities.length; i++) {
                newNormPred[i] = (float) weights[i] * unitNormPredIntensities[i];
                newNormMatched[i] = (float) weights[i] * unitNormMatchedIntensities[i];
            }

            newNormPred = subNormalize(newNormPred);
            newNormMatched = subNormalize(newNormMatched);

            //now just do euclidean distance
            double numSum = 0;
            for (int i = 0; i < predMZs.length; i++) {
                double diff = newNormPred[i] - newNormMatched[i];
                double square = diff * diff;
                numSum += square;
            }
            return 1 - Math.sqrt(numSum);
        }
    }

    public double brayCurtis() {
        if (unitNormPredIntensities == null) {
            this.unitNormalize();
        }

        //check if no matched peaks
        float floatSum = 0.0f;
        for (float f : unitNormMatchedIntensities){
            floatSum += f;
        }
        if (floatSum == 0.0f) {
            return 0;
        } else {
            double num = 0;
            double den = 0;
            for (int i = 0; i < predMZs.length; i++) {
                double exp = unitNormMatchedIntensities[i];
                double pred = unitNormPredIntensities[i];

                num += Math.abs(exp - pred);
                den += (exp + pred);
            }
            return 1 - (num / den);
        }
    }

    public double weightedBrayCurtis(double[] weights) {
        if (unitNormPredIntensities == null) {
            this.unitNormalize();
        }

        //check if no matched peaks
        float floatSum = 0.0f;
        for (float f : unitNormMatchedIntensities){
            floatSum += f;
        }
        if (floatSum == 0.0f) {
            return 0;
        } else {
            double num = 0;
            double den = 0;
            for (int i = 0; i < predMZs.length; i++) {
                double exp = unitNormMatchedIntensities[i];
                double pred = unitNormPredIntensities[i];

                num += weights[i] * Math.abs(exp - pred);
                den += weights[i] * (exp + pred);
            }
            return 1 - (num / den);
        }
    }

    public double pearsonCorr() {
        if (Arrays.stream(floatToDouble(matchedIntensities)).sum() == 0) {
            return -1; //minimum pearson correlation
        } else {
            //uses Apache
            PearsonsCorrelation pc = new PearsonsCorrelation();
            return pc.correlation(floatToDouble(matchedIntensities), floatToDouble(predIntensities));
        }
    }

    public double weightedPearsonCorr(double[] weights) {
        if (Arrays.stream(floatToDouble(matchedIntensities)).sum() == 0) {
            return -1; //minimum pearson correlation
        } else {

            double[] newPred = new double[predIntensities.length];
            double[] newMatched = new double[matchedIntensities.length];
            for (int i = 0; i < predIntensities.length; i++) {
                newPred[i] = weights[i] * predIntensities[i];
                newMatched[i] = weights[i] * matchedIntensities[i];
            }

            PearsonsCorrelation pc = new PearsonsCorrelation();
            return pc.correlation(newMatched, newPred);
        }
    }

    public double dotProduct() {
        if (unitNormPredIntensities == null) {
            this.unitNormalize();
        }

        boolean nonzero = false;
        for (float f : matchedIntensities){
            if (f > 0) {
                nonzero = true;
                break;
            }
        }
        if (nonzero) {
            double num = 0;
            for (int i = 0; i < predMZs.length; i++) {
                num += unitNormPredIntensities[i] * unitNormMatchedIntensities[i];
            }

            return num;
        } else {
            return 0;
        }
    }

    public double weightedDotProduct(double[] weights) {
        float floatSum = 0.0f;
        for (float f : matchedIntensities){
            floatSum += f;
        }
        if (floatSum == 0.0f) {
            return 0;
        } else {
            double[] newPred = new double[predIntensities.length];
            double[] newMatched = new double[matchedIntensities.length];
            for (int i = 0; i < predIntensities.length; i++) {
                newPred[i] = weights[i] * predIntensities[i];
                newMatched[i] = weights[i] * matchedIntensities[i];
            }

            double predMax = 1 / Arrays.stream(newPred).max().getAsDouble();
            double matchedMax = 1 / Arrays.stream(newMatched).max().getAsDouble();
            double multiplier = predMax * matchedMax;

            double num = 0;
            for (int i = 0; i < predMZs.length; i++) {
                num += newPred[i] * newMatched[i] * multiplier;
            }

            return num;
        }
    }

    public HashMap<String, Double> getAllSimilarities(double[] mzFreqs) {

        HashMap<String, Double> sims = new HashMap<>();

        sims.put("cosine", this.cosineSimilarity());
        sims.put("contrast", this.spectralContrastAngle());
        sims.put("euclidean", this.euclideanDistance());
        sims.put("bray-curtis", this.brayCurtis());
        sims.put("pearson", this.pearsonCorr());
        sims.put("dot", this.dotProduct());


        //weighted
        double[] weights = this.getWeights(mzFreqs);
        sims.put("weightCosine", this.weightedCosineSimilarity(weights));
        sims.put("weightContrast", this.weightedSpectralContrastAngle(weights));
        sims.put("weightEuclidean", this.weightedEuclideanDistance(weights));
        sims.put("weightBray-curtis", this.weightedBrayCurtis(weights));
        sims.put("weightPearson", this.weightedPearsonCorr(weights));
        sims.put("weightDot", this.weightedDotProduct(weights));

        return sims;
    }

    //https://stackoverflow.com/questions/4240080/generating-all-permutations-of-a-given-string
    private ArrayList<Double> calculateShuffledExpectScore(String similarityMeasure) throws NoSuchMethodException,
            IllegalAccessException, InvocationTargetException {

        //specifically use filtered version, otherwise permutation will take forever
        spectrumComparison filteredSpec = new spectrumComparison(this, true);
        double trueSim = filteredSpec.getSimilarity(similarityMeasure);

        float[] newPredInts = filteredSpec.predIntensities;
        ArrayList<Double> shuffledExpectations = new ArrayList<>();
        shuffledExpectations.add(trueSim); //first element in arraylist is the original

        //sole purpose of this class is to create permuted expectation scores
        class permutation {
            permutation(float[] added, float[] remaining) throws NoSuchMethodException, IllegalAccessException,
                    InvocationTargetException {
                int n = remaining.length;
                if (n == 0) {
                    //calculate similarity and add to list
                    //no longer need to filter, since we want to use matched intensities and unitNorm vectors as they are
                    spectrumComparison specAngle = new spectrumComparison(filteredSpec);

                    //change predicted intensities to shuffled version
                    specAngle.predIntensities = added;
                    shuffledExpectations.add(specAngle.getSimilarity(similarityMeasure));
                } else {
                    for (int i = 0; i < n; i++)
                        new permutation(ArrayUtils.addAll(added, new float[]{remaining[i]}),
                                ArrayUtils.addAll(Arrays.copyOfRange(remaining, 0, i),
                                        Arrays.copyOfRange(remaining, i + 1, n)));
                }
            }
        }
        //add shuffled expect scores
        new permutation(new float[]{}, newPredInts);
        return shuffledExpectations;
    }



//    public double[] calculateShuffledExpectScore(String similarityMeasure)
//            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
//        shufflePredIntensities(); //doesn't run if already created
//
//        double[] sims = new double[permutations.size()];
//        for (int i = 0; i < permutations.size(); i++) {
//            //new instance of object with shuffled intensities
//            spectrumComparison tmpInstance = new spectrumComparison(predMZs, permutations.get(i), matchedIntensities);
//            // calculate similarity
//            sims[i] = tmpInstance.getSimilarity(similarityMeasure);
//        }
//
//        return sims;
//        //calculate expect score
//    }

//    public double calculateShuffledExpectScore(String similarityMeasure, double[] mzFreq)
//            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
//    }

    public double getSimilarity(String similarityMeasure, double[] mzFreqs)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        boolean useWeights = (similarityMeasure.startsWith("weight"));
        if (useWeights) {
            Method method = this.getClass().getMethod(similarityMeasure, double[].class);
            double[] weights = this.getWeights(mzFreqs);
            return (double) method.invoke(this, weights);
        } else {
            Method method = this.getClass().getMethod(similarityMeasure);
            return (double) method.invoke(this);
        }
    }

    public double getSimilarity(String similarityMeasure)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        assert (!similarityMeasure.startsWith("weight"));

        Method method = this.getClass().getMethod(similarityMeasure);
        return (double) method.invoke(this);
    }

    public static void main(String[] args) throws FileParsingException, IOException {
        SpectralPredictionMapper spm1 = SpectralPredictionMapper.createSpectralPredictionMapper
                ("C:/Users/kevin/OneDriveUmich/proteomics/preds/cptacDiann.predicted.bin");

        SpectralPredictionMapper spm2 = new mgfFileReader("C:/Users/kevin/OneDriveUmich/proteomics/preds/cptacPreds.mgf");

        //iterate through peptides
        FileWriter myWriter = new FileWriter("C:/Users/kevin/Downloads/proteomics/cptac/PearsonCompare.txt");
        for (String peptide : spm1.getMzDict().keySet()) {
            try {
                spectrumComparison sc = new spectrumComparison(spm2.getMzDict().get(peptide), spm2.getIntensityDict().get(peptide),
                        spm1.getMzDict().get(peptide), spm1.getIntensityDict().get(peptide), false);
                myWriter.write(String.valueOf(sc.pearsonCorr()) + "\n");
            } catch (Exception ignored) {}
        }
        myWriter.close();
    }
}
