package lv.football.scheduler.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.CategoryChartBuilder;
import org.knowm.xchart.style.Styler.LegendPosition;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Controller
public class BenchmarkController {

    private static final Path CSV = Path.of("benchmark-results.csv");

    @GetMapping("/benchmark/data")
    @ResponseBody
    public Map<String, List<Map<String, Object>>> data() throws IOException {
        List<String> lines = Files.readAllLines(CSV);
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        if (lines.size() <= 1) return out;
        String[] header = lines.get(0).split(",");
        for (int i = 1; i < lines.size(); i++) {
            String[] cols = lines.get(i).split(",");
            String dataset = cols[0];
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("variant", cols[1]);
            row.put("timeMillis", Long.parseLong(cols[2]));
            row.put("hardScore", Integer.parseInt(cols[3]));
            row.put("softScore", Integer.parseInt(cols[4]));
            row.put("teams", Integer.parseInt(cols[5]));
            row.put("matches", Integer.parseInt(cols[6]));

            out.computeIfAbsent(dataset, k -> new ArrayList<>()).add(row);
        }
        return out;
    }

    @GetMapping("/benchmark/png")
    public ResponseEntity<byte[]> png(@RequestParam(name = "dataset", required = false) String dataset) throws IOException {
        List<String> lines = Files.readAllLines(CSV);
        if (lines.size() <= 1) return ResponseEntity.notFound().build();

        List<String> dataLines = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String l = lines.get(i);
            if (dataset == null || l.startsWith(dataset + ",")) dataLines.add(l);
        }

        // category: variants
        List<String> variants = new ArrayList<>();
        List<Integer> soft = new ArrayList<>();
        List<Long> times = new ArrayList<>();

        for (String l : dataLines) {
            String[] c = l.split(",");
            variants.add(c[1]);
            soft.add(Integer.parseInt(c[4]));
            times.add(Long.parseLong(c[2]));
        }

        CategoryChart chart = new CategoryChartBuilder().width(800).height(600)
                .title(dataset == null ? "Benchmark (all datasets)" : "Benchmark: " + dataset)
                .xAxisTitle("Variant").yAxisTitle("Value").build();
        chart.getStyler().setLegendPosition(LegendPosition.InsideNE);

        // soft score (negative numbers) -> show absolute values for clearer bars
        chart.addSeries("softScore", variants, soft);
        chart.addSeries("timeMillis", variants, times);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BitmapEncoder.saveBitmap(chart, baos, BitmapEncoder.BitmapFormat.PNG);

        byte[] bytes = baos.toByteArray();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        return ResponseEntity.ok().headers(headers).body(bytes);
    }
}
