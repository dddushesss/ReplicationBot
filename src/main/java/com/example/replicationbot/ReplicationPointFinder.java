package com.example.replicationbot;

import lombok.Data;
import lombok.var;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.awt.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.List;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Data
public class ReplicationPointFinder {
    private Image GCImage;
    private String resultMessage;
    private String ori;
    private String kmer;
    private Image kmerCounts;
    private String maxIndex;

    private String getDNAFromFile(String filename) {
        StringBuilder str = new StringBuilder();
        int i = 0;
        try (FileReader fr = new FileReader(filename)) {
            BufferedReader reader = new BufferedReader(fr);
            String line = reader.readLine();
            while (line != null) {
                if (i++ > 0)
                    str.append(line.toUpperCase(Locale.ROOT));
                line = reader.readLine();
                line = line.replaceAll("[^ATGC]", "");
            }

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        return str.toString();
    }

    private String ContainsInHashmapWithMismatch(Map<String, Set<Integer>> hashMap, String kmer, int mismatches) {

        for (Map.Entry<String, Set<Integer>> keys : hashMap.entrySet()) {
            if (isApproximateMatching(keys.getKey(), kmer, mismatches))
                return keys.getKey();
        }
        return null;
    }

    private Map<String, Set<Integer>> fetchMostFrequentPattern(String DNA, int k, int mismatches, int begin, int end) {
        Map<String, Set<Integer>> res = new HashMap<>();
        String genome = DNA.substring(begin - end, begin + end);
        for (int i = 0; i < genome.length() - k; i++) {
            String kmer = genome.substring(i, i + k);
            if (res.containsKey(kmer)) {
                Set<Integer> lst = res.get(kmer);
                lst.add(i);
            } else {
                Set<Integer> lst = new HashSet<>();
                lst.add(i);
                res.put(kmer, lst);
            }

            for (String str : getKmersOutOfDNA(kmer)) {
                if (res.containsKey(str)) {
                    Set<Integer> lst = res.get(str);
                    lst.add(i);
                } else {
                    Set<Integer> lst = new HashSet<>();
                    lst.add(i);
                    res.put(str, lst);
                }
            }
        }
        AtomicInteger i = new AtomicInteger();
        return res.entrySet().stream().peek(kmer -> {
            //System.out.print(k + " - " + String.format("%.1f", (double) i.getAndIncrement() / (double) res.size()) + '\n');
            var existKmer = ContainsInHashmapWithMismatch(res, kmer.getKey(), mismatches);
            var rev = reverse(kmer.getKey());

            if (existKmer != null || ContainsInHashmapWithMismatch(res, rev, mismatches) != null) {
                var lst = res.get(kmer.getKey());
                lst.addAll(kmer.getValue());
            }
        }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
    private List<String> getKmersOutOfDNA(String kmer) {

        var res = new ArrayList<String>();
        var letters = new char[]{'A', 'T', 'G', 'C'};
        for (int i = 0; i < kmer.length(); i++) {
            for (var c :
                    letters) {
                var stringBuiler = new StringBuilder(kmer);
                if (stringBuiler.charAt(i) == c) {
                    continue;
                }
                stringBuiler.setCharAt(i, c);
                res.add(stringBuiler.toString());
            }
        }
        return res;
    }

    private boolean isApproximateMatching(String pattern, String substring, int mismatches) {
        if (substring.length() != pattern.length()) {
            return false;
        }
        char[] patternChars = pattern.toCharArray();
        char[] substringChars = substring.toCharArray();

        for (int i = 0; i < patternChars.length; i++) {
            if (patternChars[i] != substringChars[i])
                mismatches--;
            if (mismatches < 0)
                return false;
        }
        return true;
    }

    private String reverse(String pattern) {
        StringBuilder stringBuilder = new StringBuilder(pattern.length());
        char[] chars = pattern.toCharArray();
        int length = chars.length - 1;
        for (int i = length; i > -1; i--) {
            switch (chars[i]) {
                case 'A':
                    stringBuilder.append('T');
                    break;
                case 'T':
                    stringBuilder.append('A');
                    break;
                case 'G':
                    stringBuilder.append('C');
                    break;
                case 'C':
                    stringBuilder.append('G');
                    break;
                default:
                    break;
            }
        }
        return stringBuilder.toString();
    }


    public int GCDiagram(String DNA) {
        var series = new XYSeries("Kmers");
        var GC = 0;
        var min = 0;
        var res = GC;
        for (int i = 0; i < DNA.length(); i++) {
            if (DNA.charAt(i) == 'G') {
                GC++;
            } else if (DNA.charAt(i) == 'C') {
                GC--;
            }
            if (GC < min) {
                min = GC;
                res = i;
            }
            if (i % 50 == 0)
                series.add(i, GC);
        }
        XYDataset xyDataset = new XYSeriesCollection(series);
        JFreeChart chart = ChartFactory
                .createXYLineChart("GC", "Index", "Count",
                        xyDataset,
                        PlotOrientation.VERTICAL,
                        true, true, true);

        GCImage = chart.createBufferedImage(400, 300);
        return res;
    }

    public ReplicationPointFinder(String filepath){
        try {
            execute(filepath);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void execute(String filepath) throws InterruptedException {
        String DNA = getDNAFromFile(filepath);
        var min = GCDiagram(DNA);
        if(min < 1500){
            resultMessage = "Неудаётся найти минимум на GC диаграмме.";
            return;
        }
        ExecutorService executorService = Executors.newWorkStealingPool();
        List<Callable<Map<String, Set<Integer>>>> callables = new ArrayList<>();

        for (int i = 8; i < 11; i++) {
            int finalI = i;
            callables.add(() -> fetchMostFrequentPattern(DNA, finalI, 1, min, 1500));
        }

        Map<String, Set<Integer>> AllKMers = new HashMap<>();
        executorService.invokeAll(callables).stream()
                .map(future -> {
                    try {
                        return future.get();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                })
                .forEach(AllKMers::putAll);

        AllKMers = AllKMers.entrySet().stream().filter(x -> x.getValue().size() > 5)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        HashMap<String, HashMap<Integer, Integer>> freauencyWordInWindow = new HashMap<>();
        for (var set : AllKMers.entrySet()) {


            for (int j = 0; j < DNA.length(); j += 250) {
                var k = 0;
                for (var val : set.getValue()) {
                    if (val >= j && val <= j + 500) {
                        k++;
                    }
                }

                if (!freauencyWordInWindow.containsKey(set.getKey())) {
                    HashMap<Integer, Integer> tmp = new HashMap<>();
                    tmp.put(j, k);
                    freauencyWordInWindow.put(set.getKey(), tmp);
                } else
                    freauencyWordInWindow.get(set.getKey()).put(j, k);
            }
        }
        var maxStr = "";
        var maxIndex = 0;
        var h = 0;

        for (var set : freauencyWordInWindow.entrySet()) {
            for (var set1 : set.getValue().entrySet()) {
                if (set1.getValue() > maxIndex) {
                    maxIndex = set1.getValue();
                    this.maxIndex = String.valueOf(set1.getKey() + min);
                    maxStr = set.getKey();
                    h = set1.getKey();
                }
            }
        }
        var series = new XYSeries(maxStr);
        for (var set :
                freauencyWordInWindow.get(maxStr).entrySet()) {
            series.add(set.getKey() + min - 1500, set.getValue());
        }

        XYDataset xyDataset = new XYSeriesCollection(series);
        JFreeChart chart = ChartFactory
                .createXYLineChart(maxStr, "Index", "Frequency",
                        xyDataset,
                        PlotOrientation.VERTICAL,
                        true, true, true);
        kmerCounts = chart.createBufferedImage(400,300);
        kmer = maxStr;
        ori = DNA.substring(min+h, min+h + 500);
        resultMessage = "";
    }
}
