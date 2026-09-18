import org.main.sn.web.QueryEvaluator;
import org.main.sn.dsl.Factory;
import java.nio.file.*;
import java.util.*;

public class CheckSyntax {
    static int selectEnd(String dsl) {
        int s = dsl.indexOf("SELECT(");
        if (s < 0) return -1;
        int depth = 0;
        for (int i = s + "SELECT".length(); i < dsl.length(); i++) {
            char c = dsl.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') { depth--; if (depth == 0) return i + 1; }
        }
        return -1;
    }
    static String inject(String q) {
        int at = selectEnd(q);
        if (at < 0) return q + ".FROM(\"SNGS-060\")";
        return q.substring(0, at) + ".FROM(\"SNGS-060\")" + q.substring(at);
    }
    public static void main(String[] a) throws Exception {
        List<String> lines = Files.readAllLines(Paths.get(a[0]));
        int ok = 0, bad = 0;
        for (String raw : lines) {
            String q = raw.trim();
            if (q.isEmpty() || q.startsWith("#")) continue;
            String full = q.contains(".FROM(") ? q : inject(q);
            Factory.reset();
            try {
                QueryEvaluator.parseStructure(full);
                ok++;
                System.out.println("OK    " + q);
            } catch (Throwable t) {
                bad++;
                System.out.println("ERROR " + q + "\n      -> " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
        System.out.println("\n" + ok + " ok, " + bad + " errors");
    }
}
