package space.pengtao.ledger;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 解析微信官方账单 CSV，转换为账目。 */
public class CsvImporter {

    /**
     * 解析微信账单 CSV 文本。
     * 微信账单格式：首行标题、次行表头（交易时间,交易类型,交易对方,商品,收/支,金额,支付方式,当前状态,交易单号,商户单号,备注），之后为数据行。
     */
    public static List<Transaction> parse(String content) {
        List<Transaction> out = new ArrayList<>();
        String[] lines = content.split("\n");
        boolean headerFound = false;
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.contains("微信支付账单明细")) continue; // 标题行
            if (line.contains("交易时间") && line.contains("交易类型")) { // 表头行
                headerFound = true;
                continue;
            }
            if (!headerFound) continue;

            List<String> cols = splitCsv(line);
            if (cols.size() < 6) continue;

            String timeStr = cols.get(0);
            String type = cols.get(1);
            String other = cols.get(2);
            String dir = cols.get(4);       // 收入 / 支出 / /
            String amountStr = cols.get(5); // ¥0.01
            String orderNo = cols.size() > 8 ? cols.get(8) : "";
            String remark = cols.size() > 10 ? cols.get(10) : "";

            double amount = parseAmount(amountStr);
            if (amount <= 0) continue;
            // 中性收支（提现、零钱充值等）dir 为 "/" 时跳过
            if (!dir.contains("收入") && !dir.contains("支出")) continue;

            boolean isIncome = dir.contains("收入");
            double signed = isIncome ? amount : -amount;

            Transaction t = new Transaction();
            t.amount = signed;
            t.category = guessCategory(type + " " + other + " " + remark);
            t.merchant = other.isEmpty() ? type : other;
            t.source = "wechat";
            t.note = orderNo; // 交易单号，用于去重
            t.status = 1;     // 官方账单，直接确认
            t.time = parseTime(timeStr);
            out.add(t);
        }
        return out;
    }

    /** CSV 行分割，处理引号包裹的逗号。 */
    private static List<String> splitCsv(String line) {
        List<String> cols = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
            } else if (c == ',' && !inQuote) {
                cols.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        cols.add(sb.toString().trim());
        return cols;
    }

    private static double parseAmount(String s) {
        if (s == null) return 0;
        String cleaned = s.replace("¥", "").replace("￥", "")
                .replace("元", "").replace(",", "").replace("\"", "").trim();
        try {
            return Double.parseDouble(cleaned);
        } catch (Exception e) {
            return 0;
        }
    }

    private static long parseTime(String s) {
        if (s == null) return System.currentTimeMillis();
        try {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
            return fmt.parse(s.trim()).getTime();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private static String guessCategory(String s) {
        if (s.contains("红包")) return "转账";
        if (s.contains("转账")) return "转账";
        if (s.contains("外卖") || s.contains("餐") || s.contains("饭") || s.contains("奶茶")
                || s.contains("咖啡") || s.contains("美团") || s.contains("饿了么")) return "餐饮";
        if (s.contains("打车") || s.contains("滴滴") || s.contains("地铁") || s.contains("公交")
                || s.contains("加油") || s.contains("停车") || s.contains("火车") || s.contains("机票")) return "交通";
        if (s.contains("超市") || s.contains("购物") || s.contains("淘宝") || s.contains("京东")
                || s.contains("拼多多") || s.contains("天猫") || s.contains("商场") || s.contains("便利店")) return "购物";
        if (s.contains("房租") || s.contains("水电") || s.contains("燃气") || s.contains("话费")
                || s.contains("宽带") || s.contains("物业")) return "居住";
        if (s.contains("电影") || s.contains("游戏") || s.contains("视频") || s.contains("音乐")
                || s.contains("会员") || s.contains("KTV")) return "娱乐";
        if (s.contains("收款") || s.contains("退款") || s.contains("工资") || s.contains("收入")) return "收入";
        return "其他";
    }
}
