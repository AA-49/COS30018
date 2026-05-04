package AutoNego.io;

import AutoNego.strategy.NegotiationStrategyFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Reads dealer/buyer plan CSV files for {@link AutoNego.FipaAutoNegotiationDashboard}.
 * No external CSV library — simple comma-split with optional quotes.
 */
public final class NegotiationPlanCsvReader {

    public static final class DealerRow {
        public final String agentName;
        public final String brand;
        public final String type;
        public final double sellingPrice;
        public final double minimumPrice;
        public final String tacticKey;

        public DealerRow(String agentName, String brand, String type, double sellingPrice, double minimumPrice,
                         String tacticKey) {
            this.agentName = agentName;
            this.brand = brand;
            this.type = type;
            this.sellingPrice = sellingPrice;
            this.minimumPrice = minimumPrice;
            this.tacticKey = tacticKey;
        }
    }

    public static final class BuyerRow {
        public final String agentName;
        public final String brand;
        public final String type;
        public final double startingPrice;
        public final double maximumPrice;
        public final String tacticKey;

        public BuyerRow(String agentName, String brand, String type, double startingPrice, double maximumPrice,
                        String tacticKey) {
            this.agentName = agentName;
            this.brand = brand;
            this.type = type;
            this.startingPrice = startingPrice;
            this.maximumPrice = maximumPrice;
            this.tacticKey = tacticKey;
        }
    }

    public static List<DealerRow> readDealers(Path path, List<String> errors) throws IOException {
        List<String[]> rows = readAllRows(path, errors);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> header = parseHeader(rows.get(0));
        Integer idxAgent = header.get(canonicalHeader("Dealer Agent"));
        Integer idxBrand = header.get(canonicalHeader("Brand"));
        Integer idxType = header.get(canonicalHeader("Type"));
        Integer idxSell = header.get(canonicalHeader("Selling Price"));
        Integer idxMin = header.get(canonicalHeader("Minimum Price"));
        Integer idxTactic = header.get(canonicalHeader("Tactic"));
        if (idxAgent == null || idxBrand == null || idxType == null || idxSell == null || idxMin == null) {
            errors.add("Dealers CSV missing required columns (Dealer Agent, Brand, Type, Selling Price, Minimum Price).");
            return List.of();
        }
        List<DealerRow> out = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            String[] cols = rows.get(i);
            try {
                String agent = require(cols, idxAgent, "Dealer Agent", i + 1, errors);
                String brand = require(cols, idxBrand, "Brand", i + 1, errors);
                String type = require(cols, idxType, "Type", i + 1, errors);
                double sell = parseMoney(cols, idxSell, "Selling Price", i + 1, errors);
                double min = parseMoney(cols, idxMin, "Minimum Price", i + 1, errors);
                String tacticRaw = idxTactic != null && idxTactic < cols.length ? unquote(cols[idxTactic].trim()) : "none";
                String tacticKey = NegotiationStrategyFactory.normalizeTacticKey(tacticRaw);
                out.add(new DealerRow(agent, brand, type, sell, min, tacticKey));
            } catch (ParseSkipException ignored) {
                // error already logged
            }
        }
        return out;
    }

    public static List<BuyerRow> readBuyers(Path path, List<String> errors) throws IOException {
        List<String[]> rows = readAllRows(path, errors);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> header = parseHeader(rows.get(0));
        Integer idxAgent = header.get(canonicalHeader("Buyer Agent"));
        Integer idxBrand = header.get(canonicalHeader("Find Brand"));
        Integer idxType = header.get(canonicalHeader("Find Type"));
        Integer idxStart = header.get(canonicalHeader("Starting Price"));
        Integer idxMax = header.get(canonicalHeader("Maximum Price"));
        Integer idxTactic = header.get(canonicalHeader("Tactic"));
        if (idxAgent == null || idxBrand == null || idxType == null || idxStart == null || idxMax == null) {
            errors.add("Buyers CSV missing required columns (Buyer Agent, Find Brand, Find Type, Starting Price, Maximum Price).");
            return List.of();
        }
        List<BuyerRow> out = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            String[] cols = rows.get(i);
            try {
                String agent = require(cols, idxAgent, "Buyer Agent", i + 1, errors);
                String brand = require(cols, idxBrand, "Find Brand", i + 1, errors);
                String type = require(cols, idxType, "Find Type", i + 1, errors);
                double start = parseMoney(cols, idxStart, "Starting Price", i + 1, errors);
                double max = parseMoney(cols, idxMax, "Maximum Price", i + 1, errors);
                String tacticRaw = idxTactic != null && idxTactic < cols.length ? unquote(cols[idxTactic].trim()) : "none";
                String tacticKey = NegotiationStrategyFactory.normalizeTacticKey(tacticRaw);
                out.add(new BuyerRow(agent, brand, type, start, max, tacticKey));
            } catch (ParseSkipException ignored) {
                // error already logged
            }
        }
        return out;
    }

    private static List<String[]> readAllRows(Path path, List<String> errors) throws IOException {
        List<String[]> list = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                list.add(splitCsvLine(line));
            }
        }
        return list;
    }

    private static Map<String, Integer> parseHeader(String[] row) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < row.length; i++) {
            map.put(canonicalHeader(unquote(row[i].trim())), i);
        }
        return map;
    }

    private static String canonicalHeader(String h) {
        return h.toLowerCase(Locale.ROOT).replace(" ", "");
    }

    /** Simple CSV split; handles doubled quotes inside quoted fields minimally. */
    private static String[] splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(c);
                }
            }
        }
        fields.add(cur.toString());
        return fields.toArray(new String[0]);
    }

    private static String unquote(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1).replace("\"\"", "\"");
        }
        return s;
    }

    private static String require(String[] cols, int idx, String name, int lineNo, List<String> errors) {
        if (idx >= cols.length) {
            errors.add("Line " + lineNo + ": missing " + name);
            throw new ParseSkipException();
        }
        String v = unquote(cols[idx].trim());
        if (v.isEmpty()) {
            errors.add("Line " + lineNo + ": empty " + name);
            throw new ParseSkipException();
        }
        return v;
    }

    private static double parseMoney(String[] cols, int idx, String name, int lineNo, List<String> errors) {
        if (idx >= cols.length) {
            errors.add("Line " + lineNo + ": missing " + name);
            throw new ParseSkipException();
        }
        String raw = unquote(cols[idx].trim());
        raw = raw.replace(",", "").replace("RM", "").replace("rm", "").trim();
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            errors.add("Line " + lineNo + ": invalid number for " + name + ": " + cols[idx]);
            throw new ParseSkipException();
        }
    }

    private static final class ParseSkipException extends RuntimeException {
    }
}
