package Workflow;

// prediction_tools/percolator-v3-05/bin/percolator.exe --num-threads 9 --only-psms --no-terminate --post-processing-tdc
// --results-psms cptac/2021-2-21/perc/all.tsv --decoy-results-psms cptac/2021-2-21/perc/allD.tsv
// cptac/2021-2-21/pepXMLtmp/edited_CPTAC_CCRCC_W_JHU_LUMOS_C3L-01665_T.pin
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PercolatorOutputToPepXML {

    private static final Pattern pattern = Pattern.compile("(.+spectrum=\".+\\.)([0-9]+)\\.([0-9]+)(\\.[0-9]+\".+)");

    public static void main(final String[] args) {
        if (args.length == 0) {
//            percolatorToPepXML(
//                    Paths.get("C:/Users/yangkl/Downloads/proteomics/ccrcc/top16/CPTAC_CCRCC_W_JHU_LUMOS_C3L-01665_T.pin"),
//                    "C:/Users/yangkl/Downloads/proteomics/ccrcc/top16/CPTAC_CCRCC_W_JHU_LUMOS_C3L-01665_T",
//                    Paths.get("C:/Users/yangkl/Downloads/proteomics/ccrcc/top16/all.tsv"),
//                    Paths.get("C:/Users/yangkl/Downloads/proteomics/ccrcc/top16/allD.tsv"),
//                    Paths.get("C:/Users/yangkl/Downloads/proteomics/ccrcc/top16/percToPep/interact-CPTAC_CCRCC_W_JHU_LUMOS_C3L-01665_T"),
//                    "DIA");
            percolatorToPepXML(
                    Paths.get("C:/Users/kevin/Downloads/proteomics/cptac/2021-2-21/pep1XML1tmp/percToPep/CPTAC_CCRCC_W_JHU_LUMOS_C3L-01665_T.pin"),
                    "C:/Users/kevin/Downloads/proteomics/cptac/2021-2-21/pep1XML1tmp/percToPep/CPTAC_CCRCC_W_JHU_LUMOS_C3L-01665_T",
                    Paths.get("C:/Users/kevin/Downloads/proteomics/cptac/2021-2-21/perc/all.tsv"),
                    Paths.get("C:/Users/kevin/Downloads/proteomics/cptac/2021-2-21/perc/allD.tsv"),
                    Paths.get("C:/Users/kevin/Downloads/proteomics/cptac/2021-2-21/pep1XML1tmp/percToPep/lessRT/interact-CPTAC_CCRCC_W_JHU_LUMOS_C3L-01665_T"),
                    "DIA");
        }
        else
            percolatorToPepXML(
                    Paths.get(args[0]),
                    args[1],
                    Paths.get(args[2]),
                    Paths.get(args[3]),
                    Paths.get(args[4]),
                    args[5]
            );
    }

    private static String getSpectrum(final String line) {
        String spectrum = null;
        for (final String e : line.split("\\s"))
            if (e.startsWith("spectrum=")) {
                spectrum = e.substring("spectrum=\"".length(), e.length() - 1);
                break;
            }
        return spectrum.substring(0, spectrum.length() - 2);
    }

    private static String paddingZeros(final String line) {
        Matcher matcher = pattern.matcher(line);
        if (matcher.matches()) {
            if (matcher.group(2).contentEquals(matcher.group(3))) {
                String scanNum = matcher.group(2);
                if (scanNum.length() < 5) {
                    StringBuilder sb = new StringBuilder(5);
                    for (int i = 0; i < 5 - scanNum.length(); ++i) {
                        sb.append("0");
                    }
                    sb.append(scanNum);
                    return matcher.group(1) + sb + "." + sb + matcher.group(4);
                } else {
                    return line;
                }
            } else {
                throw new RuntimeException("Cannot parse spectrum ID from  " + line);
            }
        } else {
            throw new RuntimeException("Cannot parse line " + line);
        }
    }

    private static class Spectrum_rank {
        final String spectrum;
        final int rank;

        Spectrum_rank(String spectrum, int rank) {
            this.spectrum = spectrum;
            this.rank = rank;
        }
    }

    private static Spectrum_rank get_spectrum_rank(final String s){
        final String charge_rank = s.substring(s.lastIndexOf("."));
        final int rank = Integer.parseInt(charge_rank.split("_")[1]);
        return new Spectrum_rank(s.substring(0, s.lastIndexOf(".")), rank);
    }

    private static int get_max_rank(final Path pin){
        int max_rank0 = -1;
        try (final BufferedReader brtsv = Files.newBufferedReader(pin)) {
            final String pin_header = brtsv.readLine();
            final List<String> colnames = Arrays.asList(pin_header.split("\t"));
            final int indexOf_SpecId = colnames.indexOf("SpecId");
            String line;
            while ((line = brtsv.readLine()) != null) {
                final String[] split = line.split("\t");
                final String raw_SpecId = split[indexOf_SpecId];
                final Spectrum_rank spectrum_rank = get_spectrum_rank(raw_SpecId);
                max_rank0 = Math.max(spectrum_rank.rank, max_rank0);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return max_rank0;
    }

    private static String handle_sqectrum_query(final List<String> sq,
                                                final Map<String, Object[][]> pin_tsv_dict_r,
                                                final int rank) {
        final StringBuilder sb = new StringBuilder();
        String spectrum;
        double massdiff = Double.NaN;
        double calc_neutral_pep_mass = Double.NaN;
//        sb.setLength(0);
        final Iterator<String> iterator = sq.iterator();
        for (String line; iterator.hasNext(); ) {
            line = iterator.next();
            spectrum = getSpectrum(line);
            final Object[][] tmp = pin_tsv_dict_r.get(spectrum);
            final double[] pep_score = (double[]) tmp[rank - 1][1];
            if (pep_score == null)
                return "";
            final double one_minus_PEP = 1 - pep_score[0];
            final double score = pep_score[1];
            final int[] ntt_nmc = (int[]) tmp[rank - 1][0];
            final int ntt = ntt_nmc[0];
            final int nmc = ntt_nmc[1];
            sb.append(paddingZeros(line)).append('\n');
            int isomassd = 0;
            while (iterator.hasNext()) { // fixme: the code assumes that there are always <search_hit, massdiff=, and calc_neutral_pep_mass=, which makes it not robust
                line = iterator.next();
                if (line.trim().startsWith("<search_hit ")) {
                    for (final String e : line.split("\\s")) { // fixme: the code assumes that all attributes are in one line, which makes it not robust
                        if (e.startsWith("massdiff=")) {
                            massdiff = Double.parseDouble(e.substring("massdiff=\"".length(), e.length() - 1));
                        }
                        if (e.startsWith("calc_neutral_pep_mass=")) {
                            calc_neutral_pep_mass = Double.parseDouble(e.substring("calc_neutral_pep_mass=\"".length(), e.length() - 1));
                            break;
                        }
                    }
                    double gap = Double.MAX_VALUE;
                    for (int isotope = -6; isotope < 7; ++isotope) {
                        if (Math.abs(massdiff - isotope * 1.0033548378) < gap) {
                            gap = Math.abs(massdiff - isotope * 1.0033548378);
                            isomassd = isotope;
                        }
                    }
                    if (gap > 0.1) { // It may be from an open search.
                        isomassd = 0;
                    }
                }
                if (line.trim().equals("</search_hit>")) {
                    sb.append(
                            String.format(
                                    "<analysis_result analysis=\"peptideprophet\">\n" +
                                            "<peptideprophet_result probability=\"%f\" all_ntt_prob=\"(%f,%f,%f)\">\n" +
                                            "<search_score_summary>\n" +
                                            "<parameter name=\"fval\" value=\"%f\"/>\n" +
                                            "<parameter name=\"ntt\" value=\"%d\"/>\n" +
                                            "<parameter name=\"nmc\" value=\"%d\"/>\n" +
                                            "<parameter name=\"massd\" value=\"%f\"/>\n" +
                                            "<parameter name=\"isomassd\" value=\"%d\"/>\n" +
                                            "</search_score_summary>\n" +
                                            "</peptideprophet_result>\n" +
                                            "</analysis_result>\n",
                                    one_minus_PEP, one_minus_PEP, one_minus_PEP, one_minus_PEP,
                                    score, ntt, nmc, (massdiff - isomassd * 1.0033548378) * 1e6 / calc_neutral_pep_mass, isomassd
                            ));
                }
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    public static void percolatorToPepXML(final Path pin,
                                          final String basename,
                                          final Path percolatorTargetPsms, final Path percolatorDecoyPsms,
                                          final Path outBasename,
                                          final String DIA_DDA) {

        // get max rank from pin
        final int max_rank = get_max_rank(pin);

        final Map<String, Object[][]> pin_tsv_dict_r = new HashMap<>();

        try (final BufferedReader brtsv = Files.newBufferedReader(pin)) {
            final String pin_header = brtsv.readLine();
            final List<String> colnames = Arrays.asList(pin_header.split("\t"));
            final int indexOf_SpecId = colnames.indexOf("SpecId");
            final int indexOf_ntt = colnames.indexOf("ntt");
            final int indexOf_nmc = colnames.indexOf("nmc");
            String line;
            while ((line = brtsv.readLine()) != null) {
                final String[] split = line.split("\t");
                final String raw_SpecId = split[indexOf_SpecId];
                final Spectrum_rank spectrum_rank = get_spectrum_rank(raw_SpecId);
                final String SpecId = spectrum_rank.spectrum;
                final int rank = spectrum_rank.rank;
                final int ntt = Integer.parseInt(split[indexOf_ntt]);
                final int nmc = Integer.parseInt(split[indexOf_nmc]);
                pin_tsv_dict_r.computeIfAbsent(SpecId, e -> new Object[max_rank][])
                        [rank - 1] = new Object[]{new int[]{ntt, nmc}, null};
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        for (final Path tsv : new Path[]{percolatorTargetPsms, percolatorDecoyPsms}) {
            try (final BufferedReader brtsv = Files.newBufferedReader(tsv)) {
                final String percolator_header = brtsv.readLine();
                final List<String> colnames = Arrays.asList(percolator_header.split("\t"));
                final int indexOfPSMId = colnames.indexOf("PSMId");
                final int indexOfPEP = colnames.indexOf("posterior_error_prob");
                final int indexOfScore = colnames.indexOf("score");
                String line;
                while ((line = brtsv.readLine()) != null) {
                    final String[] split = line.split("\t");
                    final String raw_psmid = split[indexOfPSMId];
                    final Spectrum_rank spectrum_rank = get_spectrum_rank(raw_psmid);
                    final String psmid = spectrum_rank.spectrum;
                    final int rank = spectrum_rank.rank;
                    final double pep = Double.parseDouble(split[indexOfPEP]);
                    final double score = Double.parseDouble(split[indexOfScore]);
                    pin_tsv_dict_r.get(psmid)[rank - 1][1] = new double[]{pep, score};
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        final boolean is_DIA = DIA_DDA.equals("DIA");
        for (int rank = 1; rank <= max_rank; ++rank) {
            final Path output_rank = is_DIA ? Paths.get(outBasename + "_rank" + rank + ".pep.xml") :
                    Paths.get(outBasename + ".pep.xml");
            final Path pepxml_rank = is_DIA ? Paths.get(basename + "_rank" + rank + ".pepXML") :
                    Paths.get(basename + ".pepXML");
            // fixme: cannot parse XML line-by-line because line break is allowed everywhere, including within an attribute, in a XML. Need to parse it using JDOM or JAXB
            try (final BufferedReader brpepxml = Files.newBufferedReader(pepxml_rank);
                 final BufferedWriter out = Files.newBufferedWriter(output_rank)) {
                String line;
                while ((line = brpepxml.readLine()) != null) {
                    out.write(line + "\n");
                    if (line.trim().startsWith("<msms_pipeline_analysis ")) {
                        final String now = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").format(LocalDateTime.now());
                        final String tmp = String.format("<analysis_summary analysis=\"database_refresh\" time=\"%s\"/>\n" +
                                        "<analysis_summary analysis=\"interact\" time=\"%s\">\n" +
                                        "<interact_summary filename=\"%s\" directory=\"\">\n" +
                                        "<inputfile name=\"%s\"/>\n" +
                                        "</interact_summary>\n" +
                                        "</analysis_summary>\n" +
                                        "<dataset_derivation generation_no=\"0\"/>\n",
                                now, now, output_rank.toAbsolutePath(), pepxml_rank.toAbsolutePath());
                        out.write(tmp);
                    }
                    if (line.trim().equals("</search_summary>"))
                        break;
                }

                while ((line = brpepxml.readLine()) != null) {
                    if (line.trim().startsWith("<spectrum_query")) {
                        final List<String> sq = new ArrayList<>();
                        sq.add(line);
                        while ((line = brpepxml.readLine()) != null) {
                            sq.add(line);
                            if (line.trim().equals("</spectrum_query>")) {
                                out.write(handle_sqectrum_query(sq, pin_tsv_dict_r, rank));
                                break;
                            }
                        }
                    }
                }
                out.write("</msms_run_summary>\n" +
                        "</msms_pipeline_analysis>");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}