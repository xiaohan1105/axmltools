package red.jiuzhou.ui.features;

import java.util.ArrayList;
import java.util.List;

import red.jiuzhou.agent.ui.AgentChatStage;
import red.jiuzhou.ui.AionMechanismExplorerStage;
import red.jiuzhou.ui.DesignerInsightStage;
import red.jiuzhou.ui.GameToolsStage;
import red.jiuzhou.ui.MechanismRelationshipStage;
import red.jiuzhou.ui.ThemeStudioStage;

/**
 * Central registry describing all launchable feature modules.
 */
public final class FeatureRegistry {

    private final List<FeatureDescriptor> descriptors;

    private FeatureRegistry(List<FeatureDescriptor> descriptors) {
        this.descriptors = new ArrayList<>(descriptors);
    }

    public static FeatureRegistry defaultRegistry() {
        List<FeatureDescriptor> features = new ArrayList<>();

        features.add(new FeatureDescriptor(
                "designer-insight",
                "设计洞察",
                "针对 XML 数据的策划洞察与一致性分析",
                FeatureCategory.ANALYTICS,
                new StageFeatureLauncher(DesignerInsightStage::new)
        ));

        features.add(new FeatureDescriptor(
                "aion-mechanism-explorer",
                "Aion机制浏览器",
                "专为Aion游戏设计的机制分类和本地化对比工具，支持27个游戏系统分类",
                FeatureCategory.ANALYTICS,
                new StageFeatureLauncher(AionMechanismExplorerStage::new)
        ));

        features.add(new FeatureDescriptor(
                "mechanism-relationship",
                "机制关系图",
                "27个游戏机制之间的依赖关系可视化，支持力导向布局和交互式探索",
                FeatureCategory.ANALYTICS,
                new StageFeatureLauncher(MechanismRelationshipStage::new)
        ));

        features.add(new FeatureDescriptor(
                "theme-studio",
                "主题工作室",
                "统一管理并应用多套 UI 主题与资源",
                FeatureCategory.DESIGN_SYSTEM,
                new StageFeatureLauncher(ThemeStudioStage::new)
        ));

        features.add(new FeatureDescriptor(
                "agent-chat",
                "AI数据助手",
                "通过自然语言查询和修改游戏数据，支持智能SQL生成、安全审核和操作回滚",
                FeatureCategory.ANALYTICS,
                new StageFeatureLauncher(AgentChatStage::new)
        ));

        features.add(new FeatureDescriptor(
                "game-tools",
                "刷怪工具",
                "刷怪点规划与概率模拟：巡逻路线生成、圆形/环形刷怪区域、怪物刷新权重验证、掉落概率测试",
                FeatureCategory.GAME_TOOLS,
                new StageFeatureLauncher(GameToolsStage::new)
        ));

        // TODO: 以下特性暂未实现，待添加对应的Stage类
        // - quest-editor: QuestEditorStage
        // - item-editor: ItemEditorStage
        // - skill-editor: SkillEditorStage
        // - npc-editor: NpcEditorStage

        return new FeatureRegistry(features);
    }

    public List<FeatureDescriptor> all() {
        return new ArrayList<>(descriptors);
    }

    public List<FeatureDescriptor> byCategory(FeatureCategory category) {
        List<FeatureDescriptor> result = new ArrayList<>();
        for (FeatureDescriptor descriptor : descriptors) {
            if (descriptor.category() == category) {
                result.add(descriptor);
            }
        }
        return result;
    }

    public FeatureDescriptor findById(String id) {
        return descriptors.stream()
                .filter(descriptor -> descriptor.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown feature id: " + id));
    }
}
