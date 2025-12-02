package red.jiuzhou.ui;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSONObject;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import red.jiuzhou.util.DatabaseUtil;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * @className: red.jiuzhou.ui.EnumQuery.java
 * @description: 枚举查询窗口
 * @author: yanxq
 * @date:  2025-04-15 20:42
 * @version V1.0
 */
public class EnumQuery {

    private static final Logger log = LoggerFactory.getLogger(EditorStage.class);
    private JdbcTemplate jdbcTemplate;

    private ComboBox<String> tableComboBox = new ComboBox<>();
    private ComboBox<String> columnComboBox = new ComboBox<>();
    private TableView<DataRow> tableView = new TableView<>();

    // 通过构造函数注入 JdbcTemplate
    public EnumQuery() {
        this.jdbcTemplate = DatabaseUtil.getJdbcTemplate();
    }

    public void showQueryWindow(Stage parentStage) {
        Stage window = new Stage();
        window.initModality(Modality.APPLICATION_MODAL);
        window.setTitle("枚举详情");

        Label tableLabel = new Label("选择表名：");
        Label columnLabel = new Label("选择字段名：");

        Button queryButton = new Button("查询");
        queryButton.setOnAction(e -> queryFileValNum(tableComboBox.getValue(), columnComboBox.getValue()));

        loadTableNames();
        tableComboBox.setOnAction(e -> loadColumnNames());

        VBox vbox = new VBox(10, tableLabel, tableComboBox, columnLabel, columnComboBox, queryButton, tableView);
        vbox.setPadding(new Insets(15));

        Scene scene = new Scene(vbox, 800, 500);
        window.setScene(scene);
        window.showAndWait();
    }

    // 使用 JdbcTemplate 从数据库加载表名
    private void loadTableNames() {
        String sql = "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()";
        List<String> tableNames = jdbcTemplate.queryForList(sql, String.class);
        ObservableList<String> allTables = FXCollections.observableArrayList(tableNames);
        tableComboBox.setItems(allTables);

        if (!allTables.isEmpty()) {
            tableComboBox.setValue(allTables.get(0));
        }
    }

    // 根据选择的表名加载字段名
    private void loadColumnNames() {
        String tableName = tableComboBox.getValue();
        if (tableName == null) return;

        String sql = "SELECT column_name FROM information_schema.columns WHERE table_name = ? AND table_schema = DATABASE()";
        List<String> columns = jdbcTemplate.queryForList(sql, String.class, tableName);
        log.info("columnNames:{}", columns.toString());
        columnComboBox.setItems(FXCollections.observableArrayList(columns));
    }

    public List<DataRow> getDataRows(String tableName, String columnName){
        // 使用 LOWER() 函数进行大小写不敏感查询
        String sql = "SELECT LOWER(" + columnName + ") AS value, COUNT(*) AS count " +
                "FROM " + tableName + " " +
                "GROUP BY LOWER(" + columnName + ")";

        List<DataRow> data = jdbcTemplate.query(sql, new RowMapper<DataRow>() {
            @Override
            public DataRow mapRow(ResultSet rs, int rowNum) throws SQLException {
                String value = rs.getString("value"); // 使用小写的值
                int count = rs.getInt("count");
                return new DataRow(value, count);
            }
        });
        return data;
    }
    // 查询数据并展示
    private void queryFileValNum(String tableName, String columnName) {
        if (tableName == null || columnName == null) return;
        List<DataRow> dataRows = getDataRows(tableName, columnName);

        tableView.getColumns().clear();
        tableView.getItems().clear();

        // "值" 列（文本类型）
        TableColumn<DataRow, String> valueColumn = new TableColumn<>("值");
        valueColumn.setCellValueFactory(dataRow -> new SimpleStringProperty(dataRow.getValue().getValue()));

        // "次数" 列（int 类型 + 可排序）
        TableColumn<DataRow, Number> countColumn = new TableColumn<>("次数");
        countColumn.setCellValueFactory(dataRow -> new SimpleIntegerProperty(dataRow.getValue().getCount()));
        countColumn.setSortable(true);

        tableView.getColumns().addAll(valueColumn, countColumn);
        tableView.setItems(FXCollections.observableArrayList(dataRows));
    }


    // 数据行类
    public static class DataRow {
        private final String value;
        private final int count;

        public DataRow(String value, int count) {
            this.value = value;
            this.count = count;
        }

        public String getValue() {
            return value;
        }

        public int getCount() {
            return count;
        }
    }

}
