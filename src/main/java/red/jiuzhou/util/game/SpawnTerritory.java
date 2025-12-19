package red.jiuzhou.util.game;

import java.util.ArrayList;
import java.util.List;

/**
 * 刷怪区域数据模型
 * 对应 world_N.xml 中的 territory 元素
 *
 * @author yanxq
 * @date 2025-01-13
 */
public class SpawnTerritory {

    /** 区域名称 */
    private String name;

    /** 天气区域名称 */
    private String weatherZoneName;

    /** 是否允许移动进入 */
    private boolean moveInTerritory = true;

    /** 是否不重生 */
    private boolean noRespawn = false;

    /** 是否生成寻路 */
    private boolean generatePathfind = false;

    /** 是否空中刷怪 */
    private boolean aerialSpawn = false;

    /** 刷怪版本 */
    private int spawnVersion = 0;

    /** 刷怪国家 (-1 = 所有) */
    private int spawnCountry = -1;

    /** 感知组ID */
    private int sensoryGroupId = 0;

    /** 广播组ID */
    private int broadcastGroupId = 0;

    /** 消失提示 */
    private int despawnHint = 0;

    /** 移动区域多边形点 (x, y 坐标列表) */
    private List<double[]> moveAreaPoints = new ArrayList<>();

    /** 刷怪点列表 (x, y, z, moveAreaIndex) */
    private List<SpawnPoint> spawnPoints = new ArrayList<>();

    /** 地面高度检测值 */
    private double checkSurfaceZ;

    /** NPC列表 */
    private List<SpawnNpc> npcs = new ArrayList<>();

    // ==================== Getters and Setters ====================

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWeatherZoneName() {
        return weatherZoneName;
    }

    public void setWeatherZoneName(String weatherZoneName) {
        this.weatherZoneName = weatherZoneName;
    }

    public boolean isMoveInTerritory() {
        return moveInTerritory;
    }

    public void setMoveInTerritory(boolean moveInTerritory) {
        this.moveInTerritory = moveInTerritory;
    }

    public boolean isNoRespawn() {
        return noRespawn;
    }

    public void setNoRespawn(boolean noRespawn) {
        this.noRespawn = noRespawn;
    }

    public boolean isGeneratePathfind() {
        return generatePathfind;
    }

    public void setGeneratePathfind(boolean generatePathfind) {
        this.generatePathfind = generatePathfind;
    }

    public boolean isAerialSpawn() {
        return aerialSpawn;
    }

    public void setAerialSpawn(boolean aerialSpawn) {
        this.aerialSpawn = aerialSpawn;
    }

    public int getSpawnVersion() {
        return spawnVersion;
    }

    public void setSpawnVersion(int spawnVersion) {
        this.spawnVersion = spawnVersion;
    }

    public int getSpawnCountry() {
        return spawnCountry;
    }

    public void setSpawnCountry(int spawnCountry) {
        this.spawnCountry = spawnCountry;
    }

    public int getSensoryGroupId() {
        return sensoryGroupId;
    }

    public void setSensoryGroupId(int sensoryGroupId) {
        this.sensoryGroupId = sensoryGroupId;
    }

    public int getBroadcastGroupId() {
        return broadcastGroupId;
    }

    public void setBroadcastGroupId(int broadcastGroupId) {
        this.broadcastGroupId = broadcastGroupId;
    }

    public int getDespawnHint() {
        return despawnHint;
    }

    public void setDespawnHint(int despawnHint) {
        this.despawnHint = despawnHint;
    }

    public List<double[]> getMoveAreaPoints() {
        return moveAreaPoints;
    }

    public void setMoveAreaPoints(List<double[]> moveAreaPoints) {
        this.moveAreaPoints = moveAreaPoints;
    }

    public List<SpawnPoint> getSpawnPoints() {
        return spawnPoints;
    }

    public void setSpawnPoints(List<SpawnPoint> spawnPoints) {
        this.spawnPoints = spawnPoints;
    }

    public double getCheckSurfaceZ() {
        return checkSurfaceZ;
    }

    public void setCheckSurfaceZ(double checkSurfaceZ) {
        this.checkSurfaceZ = checkSurfaceZ;
    }

    public List<SpawnNpc> getNpcs() {
        return npcs;
    }

    public void setNpcs(List<SpawnNpc> npcs) {
        this.npcs = npcs;
    }

    /**
     * 获取移动区域的中心点
     */
    public double[] getMoveAreaCenter() {
        if (moveAreaPoints.isEmpty()) {
            return new double[]{0, 0};
        }
        double sumX = 0, sumY = 0;
        for (double[] point : moveAreaPoints) {
            sumX += point[0];
            sumY += point[1];
        }
        return new double[]{sumX / moveAreaPoints.size(), sumY / moveAreaPoints.size()};
    }

    /**
     * 获取移动区域的边界框
     * @return [minX, minY, maxX, maxY]
     */
    public double[] getMoveAreaBounds() {
        if (moveAreaPoints.isEmpty()) {
            return new double[]{0, 0, 0, 0};
        }
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;
        for (double[] point : moveAreaPoints) {
            minX = Math.min(minX, point[0]);
            minY = Math.min(minY, point[1]);
            maxX = Math.max(maxX, point[0]);
            maxY = Math.max(maxY, point[1]);
        }
        return new double[]{minX, minY, maxX, maxY};
    }

    /**
     * 获取NPC总数
     */
    public int getTotalNpcCount() {
        int total = 0;
        for (SpawnNpc npc : npcs) {
            total += npc.getCount();
        }
        return total;
    }

    @Override
    public String toString() {
        return String.format("SpawnTerritory[%s, npcs=%d, points=%d]",
            name, npcs.size(), spawnPoints.size());
    }

    // ==================== 嵌套类 ====================

    /**
     * 刷怪点
     */
    public static class SpawnPoint {
        private double x;
        private double y;
        private double z;
        private int moveAreaIndex;

        public SpawnPoint() {}

        public SpawnPoint(double x, double y, double z, int moveAreaIndex) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.moveAreaIndex = moveAreaIndex;
        }

        public double getX() { return x; }
        public void setX(double x) { this.x = x; }

        public double getY() { return y; }
        public void setY(double y) { this.y = y; }

        public double getZ() { return z; }
        public void setZ(double z) { this.z = z; }

        public int getMoveAreaIndex() { return moveAreaIndex; }
        public void setMoveAreaIndex(int moveAreaIndex) { this.moveAreaIndex = moveAreaIndex; }

        @Override
        public String toString() {
            return String.format("(%.2f, %.2f, %.2f)", x, y, z);
        }
    }

    /**
     * 刷怪NPC配置
     */
    public static class SpawnNpc {
        /** NPC模板名称 */
        private String name;

        /** 选择概率 (-1 = 默认) */
        private int selectProb = -1;

        /** 重生时间(秒) */
        private int spawnTime = 120;

        /** 重生时间随机偏差(秒) */
        private int spawnTimeEx = 0;

        /** 刷怪数量 */
        private int count = 1;

        /** 初始刷怪时间 */
        private int initialSpawnTime = 1;

        /** 初始刷怪时间偏差 */
        private int initialSpawnTimeEx = 1;

        /** 初始刷怪数量 */
        private int initialSpawnCount = 1;

        /** 空闲生存范围 */
        private int idleLiveRange = 0;

        /** 攻击状态时消失 */
        private boolean despawnAtAttackState = false;

        // Getters and Setters

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public int getSelectProb() { return selectProb; }
        public void setSelectProb(int selectProb) { this.selectProb = selectProb; }

        public int getSpawnTime() { return spawnTime; }
        public void setSpawnTime(int spawnTime) { this.spawnTime = spawnTime; }

        public int getSpawnTimeEx() { return spawnTimeEx; }
        public void setSpawnTimeEx(int spawnTimeEx) { this.spawnTimeEx = spawnTimeEx; }

        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }

        public int getInitialSpawnTime() { return initialSpawnTime; }
        public void setInitialSpawnTime(int initialSpawnTime) { this.initialSpawnTime = initialSpawnTime; }

        public int getInitialSpawnTimeEx() { return initialSpawnTimeEx; }
        public void setInitialSpawnTimeEx(int initialSpawnTimeEx) { this.initialSpawnTimeEx = initialSpawnTimeEx; }

        public int getInitialSpawnCount() { return initialSpawnCount; }
        public void setInitialSpawnCount(int initialSpawnCount) { this.initialSpawnCount = initialSpawnCount; }

        public int getIdleLiveRange() { return idleLiveRange; }
        public void setIdleLiveRange(int idleLiveRange) { this.idleLiveRange = idleLiveRange; }

        public boolean isDespawnAtAttackState() { return despawnAtAttackState; }
        public void setDespawnAtAttackState(boolean despawnAtAttackState) { this.despawnAtAttackState = despawnAtAttackState; }

        /**
         * 获取显示用的重生时间描述
         */
        public String getSpawnTimeDisplay() {
            if (spawnTimeEx > 0) {
                return String.format("%d±%d秒", spawnTime, spawnTimeEx);
            }
            return spawnTime + "秒";
        }

        @Override
        public String toString() {
            return String.format("%s x%d (%s)", name, count, getSpawnTimeDisplay());
        }
    }
}
