package red.jiuzhou.dbxml;

import java.util.ArrayList;
import java.util.List;

public class TableNode {
    public String tableName;
    public List<TableNode> children = new ArrayList<>();
    public TableNode parent;

    public TableNode(String tableName) {
        this.tableName = tableName;
    }

    public void addChild(TableNode child) {
        children.add(child);
        child.parent = this;
    }

    public void printTree(String indent) {
        System.out.println(indent + tableName);
        for (TableNode child : children) {
            child.printTree(indent + "  ");
        }
    }
}
