package kaqqao.nutch.plugin.selector;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Test {

    public static void main(String[] args) {
        Pattern p = Pattern.compile("(\\.|#|\\[|^)([a-zA-Z0-9-_]*)(?:=(.+)\\])?", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher("div#id.class.class2[data-pid=stuff more]");

        while (m.find()) {
            String g1  = m.group(0);
            String g2  = m.group(1);
            String g3  = m.group(2);
            System.out.println(g1 + " :: " + g2 + " - " + g3);
        }
    }
}
