package dev.cosgy.niconicoSearchAPI;

import java.util.List;

public class Sample {
    public static void main(String[] args) {
        nicoSearchAPI ns = new nicoSearchAPI(true, 5);

        long start = System.currentTimeMillis();
        List<nicoVideoSearchResult> results_0 = ns.searchVideo("Hatsune Miku", 5);
        long end = System.currentTimeMillis();

        long start_2 = System.currentTimeMillis();
        List<nicoVideoSearchResult> results_1 = ns.searchVideo("Senbonzakura", 5);
        long end_2 = System.currentTimeMillis();

        long start_3 = System.currentTimeMillis();
        List<nicoVideoSearchResult> results_2 = ns.searchVideo("ODDS&ENDS", 5);
        long end_3 = System.currentTimeMillis();

        System.out.println("First: " + (end - start));
        results_0.forEach(result -> System.out.println(result.getTitle() + ": " + result.getWatchUrl()));

        System.out.println("Second: " + (end_2 - start_2));
        results_1.forEach(result -> System.out.println(result.getTitle() + ": " + result.getWatchUrl()));

        System.out.println("Third: " + (end_3 - start_3));
        results_2.forEach(result -> System.out.println(result.getTitle() + ": " + result.getWatchUrl()));
    }
}
