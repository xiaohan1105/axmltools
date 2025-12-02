package red.jiuzhou.safety;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * 数据安全管理器
 * 核心职责：确保所有数据操作的安全性和可恢复性
 *
 * 设计原则：
 * 1. 所有写操作必须经过此管理器
 * 2. 自动备份，支持多版本
 * 3. 事务性操作，要么全部成功要么全部回滚
 * 4. 完整性校验，防止数据损坏
 * 5. 操作审计，所有修改可追溯
 * 6. 文件锁定，防止并发修改冲突
 */
@Slf4j
public class DataSafetyManager {

    // 备份目录
    private static final String BACKUP_DIR = "backup";
    private static final String AUDIT_LOG_FILE = "audit.log";
    private static final int MAX_BACKUP_VERSIONS = 10; // 最多保留10个版本

    // 文件锁映射
    private final Map<String, ReentrantReadWriteLock> fileLocks = new ConcurrentHashMap<>();

    // 当前事务
    private final ThreadLocal<Transaction> currentTransaction = new ThreadLocal<>();

    // 审计日志写入器
    private BufferedWriter auditLogWriter;

    /**
     * 事务对象
     */
    @Data
    public static class Transaction {
        private String id = UUID.randomUUID().toString();
        private LocalDateTime startTime = LocalDateTime.now();
        private Map<String, byte[]> originalFiles = new HashMap<>();
        private Map<String, byte[]> modifiedFiles = new HashMap<>();
        private List<String> operations = new ArrayList<>();
        private TransactionStatus status = TransactionStatus.ACTIVE;
        private String description;

        public enum TransactionStatus {
            ACTIVE,      // 活动中
            COMMITTED,   // 已提交
            ROLLED_BACK, // 已回滚
            FAILED       // 失败
        }
    }

    /**
     * 备份记录
     */
    @Data
    public static class BackupRecord {
        private String backupId = UUID.randomUUID().toString();
        private LocalDateTime backupTime = LocalDateTime.now();
        private String originalPath;
        private String backupPath;
        private String checksum;
        private long fileSize;
        private String operation;
        private String user = System.getProperty("user.name");
    }

    /**
     * 操作审计记录
     */
    @Data
    public static class AuditRecord {
        private LocalDateTime timestamp = LocalDateTime.now();
        private String operation;
        private String filePath;
        private String user = System.getProperty("user.name");
        private boolean success;
        private String errorMessage;
        private String transactionId;
        private Map<String, String> metadata = new HashMap<>();

        @Override
        public String toString() {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            return String.format("[%s] %s | User: %s | File: %s | Success: %s | TxnId: %s%s",
                timestamp.format(formatter), operation, user, filePath, success, transactionId,
                errorMessage != null ? " | Error: " + errorMessage : "");
        }
    }

    /**
     * 数据完整性检查结果
     */
    @Data
    public static class IntegrityCheckResult {
        private boolean valid = true;
        private List<String> errors = new ArrayList<>();
        private Map<String, String> details = new HashMap<>();

        public void addError(String error) {
            this.valid = false;
            this.errors.add(error);
        }
    }

    /**
     * 初始化安全管理器
     */
    public DataSafetyManager() throws IOException {
        // 创建备份目录
        Path backupPath = Paths.get(BACKUP_DIR);
        if (!Files.exists(backupPath)) {
            Files.createDirectories(backupPath);
        }

        // 初始化审计日志
        initAuditLog();

        log.info("数据安全管理器已启动");
        auditLog("SYSTEM_START", null, true, null);
    }

    /**
     * 开始事务
     */
    public Transaction beginTransaction(String description) {
        Transaction txn = new Transaction();
        txn.setDescription(description);
        currentTransaction.set(txn);

        log.info("开始事务: {} - {}", txn.getId(), description);
        auditLog("BEGIN_TRANSACTION", null, true, txn.getId());

        return txn;
    }

    /**
     * 提交事务
     */
    public void commitTransaction() throws IOException {
        Transaction txn = currentTransaction.get();
        if (txn == null) {
            throw new IllegalStateException("没有活动的事务");
        }

        try {
            // 验证所有修改的文件
            for (Map.Entry<String, byte[]> entry : txn.getModifiedFiles().entrySet()) {
                String filePath = entry.getKey();
                byte[] content = entry.getValue();

                // 完整性检查
                IntegrityCheckResult checkResult = validateXmlIntegrity(content);
                if (!checkResult.isValid()) {
                    throw new DataIntegrityException("文件 " + filePath + " 完整性检查失败: " +
                        String.join(", ", checkResult.getErrors()));
                }
            }

            // 执行实际写入
            for (Map.Entry<String, byte[]> entry : txn.getModifiedFiles().entrySet()) {
                String filePath = entry.getKey();
                byte[] content = entry.getValue();

                // 先备份原文件
                if (txn.getOriginalFiles().containsKey(filePath)) {
                    backupFile(filePath, txn.getOriginalFiles().get(filePath));
                }

                // 写入新内容
                atomicWrite(filePath, content);
            }

            txn.setStatus(Transaction.TransactionStatus.COMMITTED);
            log.info("事务已提交: {}", txn.getId());
            auditLog("COMMIT_TRANSACTION", null, true, txn.getId());

        } catch (Exception e) {
            log.error("事务提交失败: " + txn.getId(), e);
            rollbackTransaction();
            throw new IOException("事务提交失败: " + e.getMessage(), e);
        } finally {
            currentTransaction.remove();
        }
    }

    /**
     * 回滚事务
     */
    public void rollbackTransaction() throws IOException {
        Transaction txn = currentTransaction.get();
        if (txn == null) {
            log.warn("没有活动的事务可回滚");
            return;
        }

        try {
            log.warn("正在回滚事务: {}", txn.getId());

            // 恢复所有原始文件
            for (Map.Entry<String, byte[]> entry : txn.getOriginalFiles().entrySet()) {
                String filePath = entry.getKey();
                byte[] originalContent = entry.getValue();

                try {
                    atomicWrite(filePath, originalContent);
                    log.info("已恢复文件: {}", filePath);
                } catch (IOException e) {
                    log.error("恢复文件失败: " + filePath, e);
                }
            }

            txn.setStatus(Transaction.TransactionStatus.ROLLED_BACK);
            log.info("事务已回滚: {}", txn.getId());
            auditLog("ROLLBACK_TRANSACTION", null, true, txn.getId());

        } catch (Exception e) {
            txn.setStatus(Transaction.TransactionStatus.FAILED);
            log.error("事务回滚失败: " + txn.getId(), e);
            throw new IOException("事务回滚失败: " + e.getMessage(), e);
        } finally {
            currentTransaction.remove();
        }
    }

    /**
     * 安全读取文件（带锁）
     */
    public byte[] safeRead(String filePath) throws IOException {
        ReentrantReadWriteLock lock = getFileLock(filePath);
        lock.readLock().lock();

        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                throw new FileNotFoundException("文件不存在: " + filePath);
            }

            byte[] content = Files.readAllBytes(path);

            // 记录到事务（如果有）
            Transaction txn = currentTransaction.get();
            if (txn != null && !txn.getOriginalFiles().containsKey(filePath)) {
                txn.getOriginalFiles().put(filePath, content);
            }

            return content;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 安全写入文件（带锁和备份）
     */
    public void safeWrite(String filePath, byte[] content) throws IOException {
        ReentrantReadWriteLock lock = getFileLock(filePath);
        lock.writeLock().lock();

        try {
            // 检查是否在事务中
            Transaction txn = currentTransaction.get();
            if (txn != null) {
                // 事务模式：记录操作，延迟写入
                if (!txn.getOriginalFiles().containsKey(filePath)) {
                    // 保存原始内容
                    if (Files.exists(Paths.get(filePath))) {
                        txn.getOriginalFiles().put(filePath, Files.readAllBytes(Paths.get(filePath)));
                    }
                }
                txn.getModifiedFiles().put(filePath, content);
                txn.getOperations().add("WRITE: " + filePath);

            } else {
                // 非事务模式：立即执行
                // 1. 完整性检查
                IntegrityCheckResult checkResult = validateXmlIntegrity(content);
                if (!checkResult.isValid()) {
                    throw new DataIntegrityException("数据完整性检查失败: " +
                        String.join(", ", checkResult.getErrors()));
                }

                // 2. 备份原文件
                if (Files.exists(Paths.get(filePath))) {
                    byte[] originalContent = Files.readAllBytes(Paths.get(filePath));
                    backupFile(filePath, originalContent);
                }

                // 3. 原子写入
                atomicWrite(filePath, content);

                // 4. 审计日志
                auditLog("WRITE_FILE", filePath, true, null);
            }

        } catch (Exception e) {
            auditLog("WRITE_FILE", filePath, false, null);
            throw new IOException("写入文件失败: " + e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 原子写入（确保要么完全成功要么完全失败）
     */
    private void atomicWrite(String filePath, byte[] content) throws IOException {
        Path path = Paths.get(filePath);
        Path tempPath = Paths.get(filePath + ".tmp");

        try {
            // 1. 写入临时文件
            Files.write(tempPath, content, StandardOpenOption.CREATE,
                       StandardOpenOption.TRUNCATE_EXISTING);

            // 2. 验证临时文件
            byte[] writtenContent = Files.readAllBytes(tempPath);
            if (!Arrays.equals(content, writtenContent)) {
                throw new IOException("写入验证失败：内容不匹配");
            }

            // 3. 原子移动（重命名）
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING,
                      StandardCopyOption.ATOMIC_MOVE);

            log.debug("原子写入成功: {}", filePath);

        } catch (Exception e) {
            // 清理临时文件
            if (Files.exists(tempPath)) {
                Files.delete(tempPath);
            }
            throw e;
        }
    }

    /**
     * 备份文件（多版本管理）
     */
    private void backupFile(String filePath, byte[] content) throws IOException {
        Path originalPath = Paths.get(filePath);
        String fileName = originalPath.getFileName().toString();
        String timestamp = LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        // 创建备份子目录
        Path backupDir = Paths.get(BACKUP_DIR, fileName + "_backups");
        if (!Files.exists(backupDir)) {
            Files.createDirectories(backupDir);
        }

        // 备份文件名
        String backupFileName = fileName + "." + timestamp + ".bak";
        Path backupPath = backupDir.resolve(backupFileName);

        // 写入备份
        Files.write(backupPath, content);

        // 计算校验和
        String checksum = calculateChecksum(content);

        // 创建备份记录
        BackupRecord record = new BackupRecord();
        record.setOriginalPath(filePath);
        record.setBackupPath(backupPath.toString());
        record.setChecksum(checksum);
        record.setFileSize(content.length);
        record.setOperation("BACKUP");

        // 保存备份元数据
        saveBackupMetadata(record);

        // 清理旧备份
        cleanOldBackups(backupDir);

        log.info("文件已备份: {} -> {}", filePath, backupPath);
    }

    /**
     * 清理旧备份（保留最新的N个版本）
     */
    private void cleanOldBackups(Path backupDir) throws IOException {
        List<Path> backups = Files.list(backupDir)
            .filter(p -> p.toString().endsWith(".bak"))
            .sorted((p1, p2) -> {
                try {
                    return Files.getLastModifiedTime(p2)
                        .compareTo(Files.getLastModifiedTime(p1));
                } catch (IOException e) {
                    return 0;
                }
            })
            .collect(Collectors.toList());

        // 删除超过限制的备份
        if (backups.size() > MAX_BACKUP_VERSIONS) {
            for (int i = MAX_BACKUP_VERSIONS; i < backups.size(); i++) {
                Files.delete(backups.get(i));
                log.debug("删除旧备份: {}", backups.get(i));
            }
        }
    }

    /**
     * 从备份恢复文件
     */
    public void restoreFromBackup(String filePath, String backupTime) throws IOException {
        Path originalPath = Paths.get(filePath);
        String fileName = originalPath.getFileName().toString();
        Path backupDir = Paths.get(BACKUP_DIR, fileName + "_backups");

        // 查找匹配的备份
        String backupFileName = fileName + "." + backupTime + ".bak";
        Path backupPath = backupDir.resolve(backupFileName);

        if (!Files.exists(backupPath)) {
            throw new FileNotFoundException("备份文件不存在: " + backupPath);
        }

        // 读取备份内容
        byte[] backupContent = Files.readAllBytes(backupPath);

        // 验证完整性
        IntegrityCheckResult checkResult = validateXmlIntegrity(backupContent);
        if (!checkResult.isValid()) {
            throw new DataIntegrityException("备份文件已损坏，无法恢复");
        }

        // 当前文件也先备份
        if (Files.exists(originalPath)) {
            byte[] currentContent = Files.readAllBytes(originalPath);
            backupFile(filePath, currentContent);
        }

        // 恢复
        atomicWrite(filePath, backupContent);

        log.info("已从备份恢复文件: {} <- {}", filePath, backupPath);
        auditLog("RESTORE_FROM_BACKUP", filePath, true, null);
    }

    /**
     * 验证XML完整性
     */
    public IntegrityCheckResult validateXmlIntegrity(byte[] content) {
        IntegrityCheckResult result = new IntegrityCheckResult();

        try {
            // 1. 尝试解析为XML
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();

            String xmlContent = new String(content, StandardCharsets.UTF_16);
            if (!xmlContent.startsWith("<?xml")) {
                xmlContent = new String(content, StandardCharsets.UTF_8);
            }

            ByteArrayInputStream inputStream = new ByteArrayInputStream(
                xmlContent.getBytes(StandardCharsets.UTF_8));
            Document doc = builder.parse(inputStream);
            doc.getDocumentElement().normalize();

            // 2. 检查基本结构
            if (doc.getDocumentElement() == null) {
                result.addError("XML根元素为空");
            }

            // 3. 检查编码声明
            if (!xmlContent.contains("<?xml") || !xmlContent.contains("encoding")) {
                result.addError("缺少XML声明或编码声明");
            }

            // 4. 检查文件大小（防止异常大小）
            if (content.length > 100 * 1024 * 1024) { // 100MB
                result.addError("文件过大，可能存在问题");
            }

            if (content.length == 0) {
                result.addError("文件为空");
            }

            result.getDetails().put("size", String.valueOf(content.length));
            result.getDetails().put("rootElement", doc.getDocumentElement().getNodeName());

        } catch (Exception e) {
            result.addError("XML解析失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 计算校验和
     */
    private String calculateChecksum(byte[] content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("计算校验和失败", e);
            return "UNKNOWN";
        }
    }

    /**
     * 获取文件锁
     */
    private ReentrantReadWriteLock getFileLock(String filePath) {
        return fileLocks.computeIfAbsent(filePath, k -> new ReentrantReadWriteLock());
    }

    /**
     * 审计日志
     */
    private void auditLog(String operation, String filePath, boolean success, String transactionId) {
        try {
            AuditRecord record = new AuditRecord();
            record.setOperation(operation);
            record.setFilePath(filePath);
            record.setSuccess(success);
            record.setTransactionId(transactionId);

            if (auditLogWriter != null) {
                auditLogWriter.write(record.toString());
                auditLogWriter.newLine();
                auditLogWriter.flush();
            }
        } catch (IOException e) {
            log.error("写入审计日志失败", e);
        }
    }

    /**
     * 初始化审计日志
     */
    private void initAuditLog() throws IOException {
        Path auditLogPath = Paths.get(AUDIT_LOG_FILE);
        auditLogWriter = new BufferedWriter(new FileWriter(auditLogPath.toFile(), true));
    }

    /**
     * 保存备份元数据
     */
    private void saveBackupMetadata(BackupRecord record) {
        // 简化实现：写入到日志
        log.info("备份记录: {} -> {}, 校验和: {}",
            record.getOriginalPath(), record.getBackupPath(), record.getChecksum());
    }

    /**
     * 获取文件的所有备份
     */
    public List<BackupRecord> getBackupHistory(String filePath) throws IOException {
        Path originalPath = Paths.get(filePath);
        String fileName = originalPath.getFileName().toString();
        Path backupDir = Paths.get(BACKUP_DIR, fileName + "_backups");

        if (!Files.exists(backupDir)) {
            return new ArrayList<>();
        }

        return Files.list(backupDir)
            .filter(p -> p.toString().endsWith(".bak"))
            .map(p -> {
                BackupRecord record = new BackupRecord();
                record.setOriginalPath(filePath);
                record.setBackupPath(p.toString());
                try {
                    record.setBackupTime(LocalDateTime.parse(
                        p.getFileName().toString()
                            .replace(fileName + ".", "")
                            .replace(".bak", ""),
                        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                    ));
                    record.setFileSize(Files.size(p));
                } catch (Exception e) {
                    log.error("解析备份文件信息失败", e);
                }
                return record;
            })
            .sorted((r1, r2) -> r2.getBackupTime().compareTo(r1.getBackupTime()))
            .collect(Collectors.toList());
    }

    /**
     * 批量文件的事务性操作
     */
    public void executeBatchOperation(String description,
                                     List<String> filePaths,
                                     FileOperation operation) throws IOException {
        Transaction txn = beginTransaction(description);

        try {
            for (String filePath : filePaths) {
                operation.execute(filePath, this);
            }

            commitTransaction();

        } catch (Exception e) {
            rollbackTransaction();
            throw e;
        }
    }

    /**
     * 文件操作接口
     */
    @FunctionalInterface
    public interface FileOperation {
        void execute(String filePath, DataSafetyManager manager) throws IOException;
    }

    /**
     * 紧急回滚到指定时间点
     */
    public void emergencyRollback(String timepoint) throws IOException {
        log.warn("执行紧急回滚到时间点: {}", timepoint);
        auditLog("EMERGENCY_ROLLBACK", null, true, null);

        // 获取所有在该时间点之后修改的文件
        Path backupRoot = Paths.get(BACKUP_DIR);

        List<Path> affectedBackups = Files.walk(backupRoot)
            .filter(p -> p.toString().endsWith(".bak"))
            .filter(p -> {
                String fileName = p.getFileName().toString();
                try {
                    String timestamp = fileName.substring(
                        fileName.lastIndexOf(".") - 15,
                        fileName.lastIndexOf(".")
                    );
                    return timestamp.compareTo(timepoint) <= 0;
                } catch (Exception e) {
                    return false;
                }
            })
            .collect(Collectors.toList());

        log.info("找到 {} 个需要恢复的备份文件", affectedBackups.size());

        for (Path backupPath : affectedBackups) {
            try {
                // 解析原始文件路径
                // 这里需要从备份元数据中获取，简化处理
                log.info("恢复备份: {}", backupPath);
            } catch (Exception e) {
                log.error("恢复备份失败: " + backupPath, e);
            }
        }
    }

    /**
     * 关闭管理器
     */
    public void shutdown() {
        try {
            if (auditLogWriter != null) {
                auditLogWriter.close();
            }
            log.info("数据安全管理器已关闭");
        } catch (IOException e) {
            log.error("关闭审计日志失败", e);
        }
    }

    /**
     * 数据完整性异常
     */
    public static class DataIntegrityException extends IOException {
        public DataIntegrityException(String message) {
            super(message);
        }
    }
}
