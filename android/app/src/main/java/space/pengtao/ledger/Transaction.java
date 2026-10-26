package space.pengtao.ledger;

/** 一条账目记录。amount 为正表示收入，为负表示支出，0 表示金额待补。 */
public class Transaction {
    public long id;
    public double amount;
    public String category;
    public String merchant;
    public String source; // alipay / wechat / manual
    public String note;
    public String comment; // AI 毒舌辣评
    public int status = 1; // 0=待确认，1=已确认（默认已确认，自动记账会显式改为待确认）
    public String member = ""; // 家庭成员昵称（家庭版）
    public String remark = ""; // 手动备注（说明是什么东西，全家可见）
    public long time;     // 毫秒时间戳

    public Transaction() {}
}
