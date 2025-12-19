package red.jiuzhou.util.game;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.util.YamlUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * World 刷怪服务
 * 解析和管理 world_N.xml 中的刷怪区域数据
 *
 * @author yanxq
 * @date 2025-01-13
 */
public class WorldSpawnService {

    private static final Logger log = LoggerFactory.getLogger(WorldSpawnService.class);

    /** 已加载的地图刷怪数据缓存 */
    private final Map<String, List<SpawnTerritory>> mapCache = new HashMap<>();

    /** 地图信息缓存 */
    private final Map<String, MapInfo> mapInfoCache = new HashMap<>();

    /**
     * 获取所有可用的地图目录
     */
    public List<MapInfo> getAvailableMaps() {
        List<MapInfo> maps = new ArrayList<>();
        String worldsPath = getWorldsPath();

        if (worldsPath == null || worldsPath.isEmpty()) {
            log.warn("Worlds目录路径未配置");
            return maps;
        }

        File worldsDir = new File(worldsPath);
        if (!worldsDir.exists() || !worldsDir.isDirectory()) {
            log.warn("Worlds目录不存在: {}", worldsPath);
            return maps;
        }

        File[] mapDirs = worldsDir.listFiles(File::isDirectory);
        if (mapDirs == null) {
            return maps;
        }

        for (File mapDir : mapDirs) {
            File worldNFile = new File(mapDir, "world_N.xml");
            if (worldNFile.exists()) {
                MapInfo info = new MapInfo();
                info.setName(mapDir.getName());
                info.setPath(mapDir.getAbsolutePath());
                info.setWorldNPath(worldNFile.getAbsolutePath());

                // 检查其他文件
                info.setHasWayPoint(new File(mapDir, "world_N_WayPoint_1.xml").exists());
                info.setHasWorldM(new File(mapDir, "world_M.xml").exists());

                // 估算刷怪点数量（通过文件大小）
                info.setEstimatedSize(worldNFile.length());

                maps.add(info);
                mapInfoCache.put(mapDir.getName(), info);
            }
        }

        // 按名称排序
        maps.sort(Comparator.comparing(MapInfo::getName));
        log.info("发现 {} 个地图目录", maps.size());
        return maps;
    }

    /**
     * 获取 Worlds 目录路径
     */
    private String getWorldsPath() {
        // 首先尝试从 xmlPath 中查找 Worlds 目录
        String xmlPath = YamlUtils.getProperty("xmlPath." + red.jiuzhou.util.DatabaseUtil.getDbName());
        if (xmlPath != null) {
            String[] paths = xmlPath.split(",");
            for (String path : paths) {
                if (path.trim().toLowerCase().endsWith("worlds")) {
                    return path.trim();
                }
            }
        }

        // 回退到 worldSvrDataPath
        String worldSvrPath = YamlUtils.getProperty("file.worldSvrDataPath");
        if (worldSvrPath != null && new File(worldSvrPath).exists()) {
            return worldSvrPath;
        }

        // 尝试从 aion.xmlPath 推断
        String aionXmlPath = YamlUtils.getProperty("aion.xmlPath");
        if (aionXmlPath != null) {
            File parent = new File(aionXmlPath).getParentFile();
            File worldsDir = new File(parent, "Worlds");
            if (worldsDir.exists()) {
                return worldsDir.getAbsolutePath();
            }
        }

        return null;
    }

    /**
     * 加载指定地图的所有刷怪区域
     */
    public List<SpawnTerritory> loadMapSpawns(String mapName) {
        // 检查缓存
        if (mapCache.containsKey(mapName)) {
            return mapCache.get(mapName);
        }

        MapInfo mapInfo = mapInfoCache.get(mapName);
        if (mapInfo == null) {
            getAvailableMaps(); // 刷新地图列表
            mapInfo = mapInfoCache.get(mapName);
        }

        if (mapInfo == null) {
            log.warn("地图不存在: {}", mapName);
            return Collections.emptyList();
        }

        List<SpawnTerritory> territories = parseWorldNXml(mapInfo.getWorldNPath());
        mapCache.put(mapName, territories);
        log.info("加载地图 {} 完成，共 {} 个刷怪区域", mapName, territories.size());
        return territories;
    }

    /**
     * 解析 world_N.xml 文件
     */
    private List<SpawnTerritory> parseWorldNXml(String filePath) {
        List<SpawnTerritory> territories = new ArrayList<>();

        try {
            // world_N.xml 使用 UTF-16 编码
            File file = new File(filePath);
            try (InputStreamReader reader = new InputStreamReader(
                    new FileInputStream(file), "UTF-16")) {

                SAXReader saxReader = new SAXReader();
                Document document = saxReader.read(reader);
                Element root = document.getRootElement();

                // 查找 npc_spawn 元素
                Element npcSpawn = root.element("npc_spawn");
                if (npcSpawn == null) {
                    log.warn("未找到 npc_spawn 元素: {}", filePath);
                    return territories;
                }

                // 解析所有 territory 元素
                List<Element> territoryElements = npcSpawn.elements("territory");
                for (Element territoryEl : territoryElements) {
                    SpawnTerritory territory = parseTerritory(territoryEl);
                    if (territory != null) {
                        territories.add(territory);
                    }
                }
            }
        } catch (Exception e) {
            log.error("解析 world_N.xml 失败: {}", e.getMessage(), e);
        }

        return territories;
    }

    /**
     * 解析单个 territory 元素
     */
    private SpawnTerritory parseTerritory(Element territoryEl) {
        SpawnTerritory territory = new SpawnTerritory();

        try {
            // 解析属性
            territory.setMoveInTerritory(getBooleanAttr(territoryEl, "MoveIn_Territory", true));
            territory.setNoRespawn(getBooleanAttr(territoryEl, "no_respawn", false));
            territory.setGeneratePathfind(getBooleanAttr(territoryEl, "Generate_Pathfind", false));
            territory.setAerialSpawn(getBooleanAttr(territoryEl, "Aerial_Spawn", false));
            territory.setSpawnVersion(getIntAttr(territoryEl, "spawn_version", 0));
            territory.setSpawnCountry(getIntAttr(territoryEl, "spawn_country", -1));
            territory.setSensoryGroupId(getIntAttr(territoryEl, "sensory_group_id", 0));
            territory.setBroadcastGroupId(getIntAttr(territoryEl, "broadcast_group_id", 0));
            territory.setDespawnHint(getIntAttr(territoryEl, "despawn_hint", 0));

            // 解析子元素
            territory.setName(getElementText(territoryEl, "name"));
            territory.setWeatherZoneName(getElementText(territoryEl, "weather_zone_name"));

            // 解析 points_info
            Element pointsInfo = territoryEl.element("points_info");
            if (pointsInfo != null) {
                parsePointsInfo(pointsInfo, territory);
            }

            // 解析 npcs
            Element npcsEl = territoryEl.element("npcs");
            if (npcsEl != null) {
                List<Element> npcElements = npcsEl.elements("npc");
                for (Element npcEl : npcElements) {
                    SpawnTerritory.SpawnNpc npc = parseNpc(npcEl);
                    if (npc != null) {
                        territory.getNpcs().add(npc);
                    }
                }
            }

        } catch (Exception e) {
            log.warn("解析 territory 失败: {}", e.getMessage());
            return null;
        }

        return territory;
    }

    /**
     * 解析 points_info 元素
     */
    private void parsePointsInfo(Element pointsInfo, SpawnTerritory territory) {
        // 解析 move_area_points
        Element moveAreaPoints = pointsInfo.element("move_area_points");
        if (moveAreaPoints != null) {
            List<Element> dataElements = moveAreaPoints.elements("data");
            for (Element data : dataElements) {
                double x = getDoubleElement(data, "x", 0);
                double y = getDoubleElement(data, "y", 0);
                territory.getMoveAreaPoints().add(new double[]{x, y});
            }
        }

        // 解析 points
        Element points = pointsInfo.element("points");
        if (points != null) {
            List<Element> dataElements = points.elements("data");
            for (Element data : dataElements) {
                double x = getDoubleElement(data, "x", 0);
                double y = getDoubleElement(data, "y", 0);
                double z = getDoubleElement(data, "z", 0);
                int moveAreaIndex = getIntElement(data, "moveareaindex", 0);
                territory.getSpawnPoints().add(
                    new SpawnTerritory.SpawnPoint(x, y, z, moveAreaIndex));
            }
        }

        // 解析 checksurfacez
        territory.setCheckSurfaceZ(getDoubleElement(pointsInfo, "checksurfacez", 0));
    }

    /**
     * 解析 npc 元素
     */
    private SpawnTerritory.SpawnNpc parseNpc(Element npcEl) {
        SpawnTerritory.SpawnNpc npc = new SpawnTerritory.SpawnNpc();

        npc.setSelectProb(getIntAttr(npcEl, "select_prob", -1));
        npc.setName(getElementText(npcEl, "name"));
        npc.setSpawnTime(getIntElement(npcEl, "spawn_time", 120));
        npc.setSpawnTimeEx(getIntElement(npcEl, "spawn_time_ex", 0));
        npc.setCount(getIntElement(npcEl, "count", 1));
        npc.setInitialSpawnTime(getIntElement(npcEl, "initial_spawn_time", 1));
        npc.setInitialSpawnTimeEx(getIntElement(npcEl, "initial_spawn_time_ex", 1));
        npc.setInitialSpawnCount(getIntElement(npcEl, "initial_spawn_count", 1));
        npc.setIdleLiveRange(getIntElement(npcEl, "idle_live_range", 0));
        npc.setDespawnAtAttackState(getBooleanElement(npcEl, "despawn_at_attack_state", false));

        return npc;
    }

    // ==================== XML 解析辅助方法 ====================

    private boolean getBooleanAttr(Element el, String name, boolean defaultValue) {
        String value = el.attributeValue(name);
        if (value == null) return defaultValue;
        return "TRUE".equalsIgnoreCase(value) || "true".equals(value) || "1".equals(value);
    }

    private int getIntAttr(Element el, String name, int defaultValue) {
        String value = el.attributeValue(name);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String getElementText(Element parent, String childName) {
        Element child = parent.element(childName);
        return child != null ? child.getTextTrim() : null;
    }

    private int getIntElement(Element parent, String childName, int defaultValue) {
        Element child = parent.element(childName);
        if (child == null) return defaultValue;
        try {
            return Integer.parseInt(child.getTextTrim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private double getDoubleElement(Element parent, String childName, double defaultValue) {
        Element child = parent.element(childName);
        if (child == null) return defaultValue;
        try {
            return Double.parseDouble(child.getTextTrim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean getBooleanElement(Element parent, String childName, boolean defaultValue) {
        Element child = parent.element(childName);
        if (child == null) return defaultValue;
        String value = child.getTextTrim();
        return "TRUE".equalsIgnoreCase(value) || "true".equals(value) || "1".equals(value);
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        mapCache.clear();
        mapInfoCache.clear();
    }

    /**
     * 清除指定地图的缓存
     */
    public void clearMapCache(String mapName) {
        mapCache.remove(mapName);
    }

    /**
     * 按NPC名称搜索刷怪区域
     */
    public List<SearchResult> searchByNpcName(String npcNamePattern) {
        List<SearchResult> results = new ArrayList<>();
        String pattern = npcNamePattern.toLowerCase();

        for (MapInfo mapInfo : getAvailableMaps()) {
            List<SpawnTerritory> territories = loadMapSpawns(mapInfo.getName());
            for (SpawnTerritory territory : territories) {
                for (SpawnTerritory.SpawnNpc npc : territory.getNpcs()) {
                    if (npc.getName() != null && npc.getName().toLowerCase().contains(pattern)) {
                        results.add(new SearchResult(mapInfo.getName(), territory, npc));
                    }
                }
            }
        }

        return results;
    }

    /**
     * 按区域名称搜索
     */
    public List<SearchResult> searchByTerritoryName(String territoryNamePattern) {
        List<SearchResult> results = new ArrayList<>();
        String pattern = territoryNamePattern.toLowerCase();

        for (MapInfo mapInfo : getAvailableMaps()) {
            List<SpawnTerritory> territories = loadMapSpawns(mapInfo.getName());
            for (SpawnTerritory territory : territories) {
                if (territory.getName() != null && territory.getName().toLowerCase().contains(pattern)) {
                    results.add(new SearchResult(mapInfo.getName(), territory, null));
                }
            }
        }

        return results;
    }

    /**
     * 获取地图统计信息
     */
    public MapStats getMapStats(String mapName) {
        List<SpawnTerritory> territories = loadMapSpawns(mapName);
        MapStats stats = new MapStats();
        stats.setMapName(mapName);
        stats.setTerritoryCount(territories.size());

        int totalNpcs = 0;
        int totalSpawnPoints = 0;
        Set<String> uniqueNpcs = new HashSet<>();

        for (SpawnTerritory territory : territories) {
            totalSpawnPoints += territory.getSpawnPoints().size();
            for (SpawnTerritory.SpawnNpc npc : territory.getNpcs()) {
                totalNpcs += npc.getCount();
                uniqueNpcs.add(npc.getName());
            }
        }

        stats.setTotalNpcCount(totalNpcs);
        stats.setUniqueNpcCount(uniqueNpcs.size());
        stats.setTotalSpawnPoints(totalSpawnPoints);

        return stats;
    }

    // ==================== 内部类 ====================

    /**
     * 地图信息
     */
    public static class MapInfo {
        private String name;
        private String path;
        private String worldNPath;
        private boolean hasWayPoint;
        private boolean hasWorldM;
        private long estimatedSize;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }

        public String getWorldNPath() { return worldNPath; }
        public void setWorldNPath(String worldNPath) { this.worldNPath = worldNPath; }

        public boolean isHasWayPoint() { return hasWayPoint; }
        public void setHasWayPoint(boolean hasWayPoint) { this.hasWayPoint = hasWayPoint; }

        public boolean isHasWorldM() { return hasWorldM; }
        public void setHasWorldM(boolean hasWorldM) { this.hasWorldM = hasWorldM; }

        public long getEstimatedSize() { return estimatedSize; }
        public void setEstimatedSize(long estimatedSize) { this.estimatedSize = estimatedSize; }

        /**
         * 获取文件大小的可读格式
         */
        public String getSizeDisplay() {
            if (estimatedSize < 1024) {
                return estimatedSize + " B";
            } else if (estimatedSize < 1024 * 1024) {
                return String.format("%.1f KB", estimatedSize / 1024.0);
            } else {
                return String.format("%.1f MB", estimatedSize / (1024.0 * 1024));
            }
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * 搜索结果
     */
    public static class SearchResult {
        private String mapName;
        private SpawnTerritory territory;
        private SpawnTerritory.SpawnNpc matchedNpc;

        public SearchResult(String mapName, SpawnTerritory territory, SpawnTerritory.SpawnNpc matchedNpc) {
            this.mapName = mapName;
            this.territory = territory;
            this.matchedNpc = matchedNpc;
        }

        public String getMapName() { return mapName; }
        public SpawnTerritory getTerritory() { return territory; }
        public SpawnTerritory.SpawnNpc getMatchedNpc() { return matchedNpc; }
    }

    /**
     * 地图统计
     */
    public static class MapStats {
        private String mapName;
        private int territoryCount;
        private int totalNpcCount;
        private int uniqueNpcCount;
        private int totalSpawnPoints;

        public String getMapName() { return mapName; }
        public void setMapName(String mapName) { this.mapName = mapName; }

        public int getTerritoryCount() { return territoryCount; }
        public void setTerritoryCount(int territoryCount) { this.territoryCount = territoryCount; }

        public int getTotalNpcCount() { return totalNpcCount; }
        public void setTotalNpcCount(int totalNpcCount) { this.totalNpcCount = totalNpcCount; }

        public int getUniqueNpcCount() { return uniqueNpcCount; }
        public void setUniqueNpcCount(int uniqueNpcCount) { this.uniqueNpcCount = uniqueNpcCount; }

        public int getTotalSpawnPoints() { return totalSpawnPoints; }
        public void setTotalSpawnPoints(int totalSpawnPoints) { this.totalSpawnPoints = totalSpawnPoints; }
    }
}
