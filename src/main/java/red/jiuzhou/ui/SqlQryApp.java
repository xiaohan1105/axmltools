package red.jiuzhou.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import red.jiuzhou.tabmapping.MappingLoader;
import red.jiuzhou.tabmapping.SqlConverter;
import red.jiuzhou.tabmapping.TableMapping;
import red.jiuzhou.util.DatabaseUtil;
import red.jiuzhou.util.JSONRecord;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class SqlQryApp {

    private static final Logger log = LoggerFactory.getLogger(SqlQryApp.class);
    static List<TableMapping> mappings = MappingLoader.loadMappings();

    private int tabCount = 1;

    public Stage show() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

        Button newTabBtn = new Button("➕ 新建查询窗口");
        newTabBtn.setOnAction(e -> {
            Tab newTab = createQueryTab();
            tabPane.getTabs().add(newTab);
            tabPane.getSelectionModel().select(newTab);
        });

        ToolBar topBar = new ToolBar(newTabBtn);
        topBar.setStyle("-fx-background-color: linear-gradient(to right, #f0f0f0, #ffffff);");

        VBox root = new VBox(topBar, tabPane);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        Scene scene = new Scene(root, 1360, 660);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());

        Stage stage = new Stage();
        stage.setScene(scene);
        stage.setTitle("🧪 SQL 查询器");
        stage.show();

        // 默认新建一个查询页
        newTabBtn.fire();
        return stage;
    }

    private Tab createQueryTab() {
        TextArea sqlEditor = new TextArea("");
        sqlEditor.setFont(Font.font("Consolas", 14));
        sqlEditor.setStyle("-fx-control-inner-background: #fefefe; -fx-border-color: #ccc;");
        Button sqlConverterBtn = new Button("\uD83D\uDD04 SQL转换");
        Button executeBtn = new Button("🚀 执行 SQL");
        Label statusLabel = new Label("等待执行...");
        statusLabel.setTextFill(Color.GRAY);

        TableView<Map<String, Object>> resultTable = new TableView<>();
        resultTable.setPlaceholder(new Label("暂无数据"));
        resultTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        ToolBar queryBar = new ToolBar(sqlConverterBtn, executeBtn, new Separator(), statusLabel);
        queryBar.setStyle("-fx-background-color: #fafafa;");

        VBox editorBox = new VBox(sqlEditor, queryBar);
        VBox.setVgrow(sqlEditor, Priority.ALWAYS);

        TabPane resultTabPane = new TabPane();
        resultTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(Orientation.VERTICAL);
        splitPane.getItems().addAll(editorBox, resultTabPane);
        splitPane.setDividerPositions(0.4);

        Tab tab = new Tab("Query " + tabCount++);
        tab.setContent(splitPane);

        executeBtn.setOnAction(e -> {
            resultTabPane.getTabs().clear();
            String sql = sqlEditor.getText();
            statusLabel.setText("执行中...");
            statusLabel.setTextFill(Color.BLUE);
            executeSql(sql, resultTabPane, statusLabel);
        });

        sqlConverterBtn.setOnAction(e -> {
            String sql = sqlEditor.getText();
            statusLabel.setTextFill(Color.BLUE);
            new Thread(() -> {
                String actualSql = sql.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("--.*", "").trim().toUpperCase();
                if (!actualSql.startsWith("INSERT") && !actualSql.startsWith("UPDATE")) {
                    Platform.runLater(() -> {
                        statusLabel.setText("❌ 仅支持 INSERT 和 UPDATE 语句");
                        statusLabel.setTextFill(Color.RED);
                    });
                    return;
                }
                String convertSql = "";
                log.info("开始转换 SQL: {}", sql);
                if(sql.trim().toUpperCase().startsWith("INSERT")){
                    convertSql = SqlConverter.convert(sql, mappings, true);
                }else{
                    convertSql = SqlConverter.convert(sql, mappings, false);
                }
                log.info("convertSql:{}", convertSql);
                String finalConvertSql = convertSql;
                Platform.runLater(() -> {
                    sqlEditor.setText(sql + "\r\n" + finalConvertSql);
                });
                Platform.runLater(() -> {
                    statusLabel.setText("转换完成");
                    statusLabel.setTextFill(Color.GREEN);
                });

            }).start();
        });
        // 添加右键菜单
        ContextMenu contextMenu = new ContextMenu();
        MenuItem executeSelected = new MenuItem("🚀 执行选中 SQL");
        contextMenu.getItems().add(executeSelected);
        sqlEditor.setContextMenu(contextMenu);

        // 设置菜单事件
        executeSelected.setOnAction(event -> {
            String selectedText = sqlEditor.getSelectedText().trim();
            if (selectedText.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "请先选中 SQL 内容后再执行", ButtonType.OK);
                alert.showAndWait();
                return;
            }
            resultTabPane.getTabs().clear();
            statusLabel.setText("执行中选中 SQL...");
            statusLabel.setTextFill(Color.BLUE);

            executeSql(selectedText, resultTabPane, statusLabel);
        });

        return tab;
    }
    private List<Map<String, Object>> executeSql(String sql) {
        JdbcTemplate jdbcTemplate = DatabaseUtil.getJdbcTemplate();
        List<Map<String, Object>> data = new ArrayList<>();
        String upperSql = sql.trim().toUpperCase();

        try {
            if (upperSql.startsWith("SELECT")) {
                data = jdbcTemplate.query(sql, (rs, rowNum) -> {
                    ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    return row;
                });
            } else {
                int updateCount = jdbcTemplate.update(sql);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("影响行数", updateCount);
                data.add(result);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("错误信息", e.getMessage());
            data.clear();
            data.add(error);
        }

        return data;
    }


    // 渲染结果表格
    /*private void updateTable(TableView<Map<String, Object>> table, List<Map<String, Object>> data) {
        table.getColumns().clear();
        if (!data.isEmpty()) {
            Map<String, Object> sample = data.get(0);
            for (String key : sample.keySet()) {
                TableColumn<Map<String, Object>, Object> col = new TableColumn<>(key);
                col.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().get(key)));
                table.getColumns().add(col);
            }
            table.getItems().setAll(data);
        } else {
            table.setPlaceholder(new Label("查询结果为空"));
        }
    }*/

    private List<Map<String, Object>> executeMultiSql(String multiSql) {
        JdbcTemplate jdbcTemplate = DatabaseUtil.getJdbcTemplate();
        List<Map<String, Object>> totalData = new ArrayList<>();

        String[] statements = multiSql.split("(?<=;)");
        for (String rawSql : statements) {
            String sql = rawSql.trim();
            if (sql.isEmpty()) {
                continue;
            }
            String sqlTag = sql.length() > 60 ? sql.substring(0, 60) + "..." : sql;

            try {
                if (sql.toUpperCase().startsWith("SELECT")) {
                    List<Map<String, Object>> result = jdbcTemplate.query(sql, (rs, rowNum) -> {
                        ResultSetMetaData meta = rs.getMetaData();
                        int colCount = meta.getColumnCount();
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= colCount; i++) {
                            row.put(meta.getColumnLabel(i), rs.getObject(i));
                        }
                        row.put("_来源SQL", sqlTag);
                        return row;
                    });
                    totalData.addAll(result);
                } else {
                    int affected = jdbcTemplate.update(sql);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("_来源SQL", sqlTag);
                    row.put("影响行数", affected);
                    totalData.add(row);
                }
            } catch (Exception e) {
                Map<String, Object> errorRow = new LinkedHashMap<>();
                errorRow.put("_来源SQL", sqlTag);
                errorRow.put("错误信息", e.getMessage());
                totalData.add(errorRow);
            }
        }

        return totalData;
    }

    private void updateTable(TableView<Map<String, Object>> table, List<Map<String, Object>> data) {
        table.getColumns().clear();
        if (!data.isEmpty()) {
            Map<String, Object> sample = data.get(0);
            for (String key : sample.keySet()) {
                TableColumn<Map<String, Object>, Object> col = new TableColumn<>(key);
                col.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().get(key)));
                table.getColumns().add(col);
            }
            table.getItems().setAll(data);
        } else {
            table.setPlaceholder(new Label("查询结果为空"));
        }
    }

    private void executeSql(String sql, TabPane resultTabPane) {
        JdbcTemplate jdbcTemplate = DatabaseUtil.getJdbcTemplate();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.query(sql, (rs, rowNum) -> {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= colCount; i++) {
                    row.put(meta.getColumnLabel(i), rs.getObject(i));
                }
                return row;
            });

            Platform.runLater(() -> {
                TableView<Map<String, Object>> table = new TableView<>();
                table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
                if (!rows.isEmpty()) {
                    Map<String, Object> firstRow = rows.get(0);
                    for (String colName : firstRow.keySet()) {
                        TableColumn<Map<String, Object>, Object> col = new TableColumn<>(colName);
                        col.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().get(colName)));
                        table.getColumns().add(col);
                    }
                }
                table.getItems().setAll(rows);
                Tab resultTab = new Tab(sql.length() > 30 ? sql.substring(0, 30) + "..." : sql);
                resultTab.setContent(table);
                resultTabPane.getTabs().add(resultTab);
            });

        } catch (Exception e) {
            Platform.runLater(() -> {
                TextArea errorArea = new TextArea("❌ 执行出错：" + e.getMessage());
                errorArea.setWrapText(true);
                errorArea.setEditable(false);
                Tab errorTab = new Tab("错误: " + (sql.length() > 20 ? sql.substring(0, 20) + "..." : sql));
                errorTab.setContent(errorArea);
                resultTabPane.getTabs().add(errorTab);
            });
        }
    }

    private void executeSql(String fullSql, TabPane resultTabPane, Label statusLabel) {
        JdbcTemplate jdbcTemplate = DatabaseUtil.getJdbcTemplate();

        new Thread(() -> {
            StringBuilder cleanedSql = new StringBuilder();
            for (String line : fullSql.split("\\R")) { // 按行处理
                String trimmed = line.trim();
                if (!trimmed.startsWith("--") && !trimmed.isEmpty()) {
                    cleanedSql.append(line).append("\n");
                }
            }

            // 再按“分号+可选空白”分割
            String[] sqls = cleanedSql.toString().split("(?<=;)[\\s]*");
            int successCount = 0, failCount = 0;

            for (String sql : sqls) {
                String trimmed = sql.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("--") || trimmed.startsWith("#")){
                    continue;
                }

                try {
                    if (trimmed.toUpperCase().startsWith("SELECT")) {
                        List<Map<String, Object>> result = jdbcTemplate.query(trimmed, (rs, rowNum) -> {
                            ResultSetMetaData meta = rs.getMetaData();
                            int colCount = meta.getColumnCount();
                            Map<String, Object> row = new LinkedHashMap<>();
                            for (int i = 1; i <= colCount; i++) {
                                row.put(meta.getColumnLabel(i), rs.getObject(i));
                            }
                            return row;
                        });

                        Platform.runLater(() -> {
                            TableView<Map<String, Object>> tableView = buildTableView(result);

                            Tab resultTab = new Tab("执行sql:" + trimmed + "\n结果 " + (resultTabPane.getTabs().size() + 1), tableView);
                            resultTabPane.getTabs().add(resultTab);
                            resultTabPane.getSelectionModel().select(resultTab);
                        });
                    } else {
                        int updateCount = jdbcTemplate.update(trimmed);
                        Platform.runLater(() -> {
                            TextArea updateText = new TextArea("执行sql:" + trimmed + "\n更新成功，影响行数：" + updateCount);
                            updateText.setEditable(false);
                            Tab resultTab = new Tab("结果 " + (resultTabPane.getTabs().size() + 1), updateText);
                            resultTabPane.getTabs().add(resultTab);
                            resultTabPane.getSelectionModel().select(resultTab);
                        });
                    }
                    successCount++;
                } catch (Exception e) {
                    String msg = e.getMessage();
                    Pattern colPattern = Pattern.compile("Data too long for column '(.+?)'");
                    Pattern tablePattern = Pattern.compile("(?i)insert\\s+into\\s+[`]?([a-zA-Z0-9_]+)[`]?");

                    Matcher colMatcher = colPattern.matcher(msg);
                    Matcher tableMatcher = tablePattern.matcher(msg);
                    if (colMatcher.find() && tableMatcher.find()) {
                        String columnName = colMatcher.group(1);
                        String realTableName = tableMatcher.group(1);
                        // 获取当前长度
                        String infoSchemaSql = "SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS " +
                                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
                        Integer oldLength = DatabaseUtil.getJdbcTemplate().queryForObject(infoSchemaSql, new Object[]{realTableName, columnName}, Integer.class);

                        if (oldLength == null || oldLength <= 0) {
                            throw new RuntimeException("无法获取字段原始长度");
                        }

                        int newLength = oldLength * 2;

                        String alterSql = String.format("ALTER TABLE `%s` MODIFY COLUMN `%s` VARCHAR(%d)", realTableName, columnName, newLength);
                        System.out.println("字段过长，尝试修改字段长度并重试：" + alterSql);
                        DatabaseUtil.getJdbcTemplate().execute(alterSql);
                        // 不跳过当前记录，重试
                    }

                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    String errorMsg = sw.toString();
                    Platform.runLater(() -> {
                        TextArea errorText = new TextArea(errorMsg);
                        errorText.setEditable(false);
                        errorText.setStyle("-fx-text-fill: red;");
                        Tab errorTab = new Tab("错误 " + (resultTabPane.getTabs().size() + 1), errorText);
                        resultTabPane.getTabs().add(errorTab);
                        resultTabPane.getSelectionModel().select(errorTab);
                    });
                    failCount++;
                }
            }

            int finalSuccessCount = successCount;
            int finalFailCount = failCount;
            Platform.runLater(() -> {
                statusLabel.setText("执行完成，成功：" + finalSuccessCount + " 条，失败：" + finalFailCount + " 条");
                statusLabel.setTextFill(finalFailCount == 0 ? Color.GREEN : Color.ORANGE);
            });
        }).start();
    }


    private TableView<Map<String, Object>> buildTableView(List<Map<String, Object>> data) {
        TableView<Map<String, Object>> tableView = new TableView<>();

        if (data.isEmpty()) {
            return tableView;
        }

        Map<String, Object> firstRow = data.get(0);
        for (String columnKey : firstRow.keySet()) {
            TableColumn<Map<String, Object>, Object> column = new TableColumn<>(columnKey);
            column.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue().get(columnKey)));
            tableView.getColumns().add(column);
        }

        tableView.getItems().addAll(data);
        return tableView;
    }


}
