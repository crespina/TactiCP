import org.main.sn.web.QueryEvaluator;
import org.main.sn.dsl.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class RunCorpus {
    static int selectEnd(String dsl) {
        int s = dsl.indexOf("SELECT(");
        if (s < 0) return -1;
        int depth = 0;
        for (int i = s + 6; i < dsl.length(); i++) {
            char c = dsl.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') { depth--; if (depth == 0) return i + 1; }
        }
        return -1;
    }
    public static void main(String[] a) throws Exception {
        List<String> qs = Files.readAllLines(Paths.get(a[0]));
        String ds = a.length > 1 ? a[1] : "SNGS-060";
        long perQ = a.length > 2 ? Long.parseLong(a[2]) : 60;
        ExecutorService ex = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r); t.setDaemon(true); return t; });
        int i = 0;
        for (String raw : qs) {
            String q0 = raw.trim();
            if (q0.isEmpty()) continue;
            i++;
            int at = selectEnd(q0);
            final String q = q0.substring(0, at) + ".FROM(\"" + ds + "\")" + q0.substring(at);
            Factory.reset();
            long t0 = System.currentTimeMillis();
            Future<String> f = ex.submit(() -> {
                Object o = QueryEvaluator.evaluate(q);
                int n = (o instanceof Integer x) ? x : ((SelectExpr) o).search().size();
                return n + " matches";
            });
            String res;
            try { res = f.get(perQ, TimeUnit.SECONDS); }
            catch (TimeoutException te) { f.cancel(true); res = "TIMEOUT"; }
            catch (ExecutionException ee) {
                Throwable c = ee.getCause();
                res = "!! " + c.getClass().getSimpleName() + ": " + String.valueOf(c.getMessage()).replace('\n',' ');
            }
            System.out.printf("%3d  %-9s %6dms  %s%n", i, res.startsWith("!!") || res.equals("TIMEOUT") ? "FAIL" : "ok", System.currentTimeMillis()-t0, res);
            System.out.flush();
            if (res.equals("TIMEOUT")) {
                // a cancelled solver thread may be wedged; get a fresh one
                ex.shutdownNow();
                ex = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r); t.setDaemon(true); return t; });
            }
        }
        System.out.println("done");
    }
}
