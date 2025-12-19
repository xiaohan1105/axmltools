DROP TABLE IF EXISTS xmldb_suiyue.quest;
CREATE TABLE xmldb_suiyue.quest (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` TEXT COMMENT 'name',
    `dev_name` TEXT COMMENT 'dev_name',
    `desc` TEXT COMMENT 'desc',
    `category1` TEXT COMMENT 'category1',
    `category2` TEXT COMMENT 'category2',
    `f_mission` TEXT COMMENT 'f_mission',
    `max_repeat_count` TEXT COMMENT 'max_repeat_count',
    `client_level` TEXT COMMENT 'client_level',
    `minlevel_permitted` TEXT COMMENT 'minlevel_permitted',
    `maxlevel_permitted` TEXT COMMENT 'maxlevel_permitted',
    `gender_permitted` TEXT COMMENT 'gender_permitted',
    `bm_restrict_category` TEXT COMMENT 'bm_restrict_category',
    `reward_exp1` TEXT COMMENT 'reward_exp1',
    `reward_gold1` TEXT COMMENT 'reward_gold1',
    `class_permitted` TEXT COMMENT 'class_permitted',
    `race_permitted` TEXT COMMENT 'race_permitted',
    `cannot_giveup` TEXT COMMENT 'cannot_giveup',
    `cannot_share` TEXT COMMENT 'cannot_share',
    `__type_desc__` TEXT COMMENT '__type_desc__',
    `collect_progress` TEXT COMMENT 'collect_progress',
    `collect_item1` TEXT COMMENT 'collect_item1',
    `drop_monster_1` TEXT COMMENT 'drop_monster_1',
    `drop_item_1` TEXT COMMENT 'drop_item_1',
    `drop_prob_1` TEXT COMMENT 'drop_prob_1',
    `drop_each_member_1` TEXT COMMENT 'drop_each_member_1',
    `selectable_reward_item1_1` TEXT COMMENT 'selectable_reward_item1_1',
    `selectable_reward_item1_2` TEXT COMMENT 'selectable_reward_item1_2',
    `selectable_reward_item1_3` TEXT COMMENT 'selectable_reward_item1_3',
    `check_item1_1` TEXT COMMENT 'check_item1_1',
    `drop_monster_2` TEXT COMMENT 'drop_monster_2',
    `drop_item_2` TEXT COMMENT 'drop_item_2',
    `drop_prob_2` TEXT COMMENT 'drop_prob_2',
    `drop_each_member_2` TEXT COMMENT 'drop_each_member_2',
    `selectable_reward_item1_4` TEXT COMMENT 'selectable_reward_item1_4',
    `selectable_reward_item1_5` TEXT COMMENT 'selectable_reward_item1_5',
    `selectable_reward_item1_6` TEXT COMMENT 'selectable_reward_item1_6',
    `reward_title1` TEXT COMMENT 'reward_title1',
    `use_class_reward` TEXT COMMENT 'use_class_reward',
    `reward_exp2` TEXT COMMENT 'reward_exp2',
    `reward_exp3` TEXT COMMENT 'reward_exp3',
    `reward_exp4` TEXT COMMENT 'reward_exp4',
    `reward_exp5` TEXT COMMENT 'reward_exp5',
    `reward_exp6` TEXT COMMENT 'reward_exp6',
    `fighter_selectable_reward` TEXT COMMENT 'fighter_selectable_reward',
    `knight_selectable_reward` TEXT COMMENT 'knight_selectable_reward',
    `ranger_selectable_reward` TEXT COMMENT 'ranger_selectable_reward',
    `assassin_selectable_reward` TEXT COMMENT 'assassin_selectable_reward',
    `wizard_selectable_reward` TEXT COMMENT 'wizard_selectable_reward',
    `elementalist_selectable_reward` TEXT COMMENT 'elementalist_selectable_reward',
    `priest_selectable_reward` TEXT COMMENT 'priest_selectable_reward',
    `chanter_selectable_reward` TEXT COMMENT 'chanter_selectable_reward',
    `gunner_selectable_reward` TEXT COMMENT 'gunner_selectable_reward',
    `bard_selectable_reward` TEXT COMMENT 'bard_selectable_reward',
    `rider_selectable_reward` TEXT COMMENT 'rider_selectable_reward',
    `reward_item1_1` TEXT COMMENT 'reward_item1_1',
    `reward_item1_2` TEXT COMMENT 'reward_item1_2',
    `collect_item2` TEXT COMMENT 'collect_item2',
    `selectable_reward_item1_7` TEXT COMMENT 'selectable_reward_item1_7',
    `drop_monster_3` TEXT COMMENT 'drop_monster_3',
    `drop_item_3` TEXT COMMENT 'drop_item_3',
    `drop_prob_3` TEXT COMMENT 'drop_prob_3',
    `drop_each_member_3` TEXT COMMENT 'drop_each_member_3',
    `drop_monster_4` TEXT COMMENT 'drop_monster_4',
    `drop_item_4` TEXT COMMENT 'drop_item_4',
    `drop_prob_4` TEXT COMMENT 'drop_prob_4',
    `drop_each_member_4` TEXT COMMENT 'drop_each_member_4',
    `finished_quest_cond1` TEXT COMMENT 'finished_quest_cond1',
    `reward_item2_1` TEXT COMMENT 'reward_item2_1',
    `reward_gold2` TEXT COMMENT 'reward_gold2',
    `selectable_reward_item1_8` TEXT COMMENT 'selectable_reward_item1_8',
    `selectable_reward_item1_9` TEXT COMMENT 'selectable_reward_item1_9',
    `selectable_reward_item1_10` TEXT COMMENT 'selectable_reward_item1_10',
    `collect_item3` TEXT COMMENT 'collect_item3',
    `collect_item4` TEXT COMMENT 'collect_item4',
    `check_item1_2` TEXT COMMENT 'check_item1_2',
    `check_item1_3` TEXT COMMENT 'check_item1_3',
    `check_item1_4` TEXT COMMENT 'check_item1_4',
    `reward_item1_3` TEXT COMMENT 'reward_item1_3',
    `selectable_reward_item1_11` TEXT COMMENT 'selectable_reward_item1_11',
    `selectable_reward_item1_12` TEXT COMMENT 'selectable_reward_item1_12',
    `reward_abyss_point1` TEXT COMMENT 'reward_abyss_point1',
    `reward_item1_4` TEXT COMMENT 'reward_item1_4',
    `reward_item1_5` TEXT COMMENT 'reward_item1_5',
    `quest_work_item1` TEXT COMMENT 'quest_work_item1',
    `quest_work_item2` TEXT COMMENT 'quest_work_item2',
    `quest_work_item3` TEXT COMMENT 'quest_work_item3',
    `quest_work_item4` TEXT COMMENT 'quest_work_item4',
    `selectable_reward_item2_1` TEXT COMMENT 'selectable_reward_item2_1',
    `selectable_reward_item2_2` TEXT COMMENT 'selectable_reward_item2_2',
    `reward_title2` TEXT COMMENT 'reward_title2',
    `reward_item3_1` TEXT COMMENT 'reward_item3_1',
    `reward_gold3` TEXT COMMENT 'reward_gold3',
    `mobile_event` TEXT COMMENT 'mobile_event',
    `inventory_item_name1` TEXT COMMENT 'inventory_item_name1',
    `extra_category` TEXT COMMENT 'extra_category',
    `check_item2_1` TEXT COMMENT 'check_item2_1',
    `acquired_quest_cond1` TEXT COMMENT 'acquired_quest_cond1',
    `reward_repeat_count` TEXT COMMENT 'reward_repeat_count',
    `reward_gold_ext` TEXT COMMENT 'reward_gold_ext',
    `reward_item_ext_1` TEXT COMMENT 'reward_item_ext_1',
    `title` TEXT COMMENT 'title',
    `reward_item4_1` TEXT COMMENT 'reward_item4_1',
    `inventory_item_name2` TEXT COMMENT 'inventory_item_name2',
    `check_item3_1` TEXT COMMENT 'check_item3_1',
    `reward_gold4` TEXT COMMENT 'reward_gold4',
    `check_item4_1` TEXT COMMENT 'check_item4_1',
    `selectable_reward_item1_13` TEXT COMMENT 'selectable_reward_item1_13',
    `finished_quest_cond2` TEXT COMMENT 'finished_quest_cond2',
    `reward_item2_2` TEXT COMMENT 'reward_item2_2',
    `reward_item3_2` TEXT COMMENT 'reward_item3_2',
    `unfinished_quest_cond1` TEXT COMMENT 'unfinished_quest_cond1',
    `noacquired_quest_cond1` TEXT COMMENT 'noacquired_quest_cond1',
    `quest_repeat_cycle` TEXT COMMENT 'quest_repeat_cycle',
    `reward_abyss_point2` TEXT COMMENT 'reward_abyss_point2',
    `selectable_reward_item2_3` TEXT COMMENT 'selectable_reward_item2_3',
    `selectable_reward_item2_4` TEXT COMMENT 'selectable_reward_item2_4',
    `selectable_reward_item2_5` TEXT COMMENT 'selectable_reward_item2_5',
    `reward_abyss_point3` TEXT COMMENT 'reward_abyss_point3',
    `selectable_reward_item3_1` TEXT COMMENT 'selectable_reward_item3_1',
    `selectable_reward_item3_2` TEXT COMMENT 'selectable_reward_item3_2',
    `selectable_reward_item3_3` TEXT COMMENT 'selectable_reward_item3_3',
    `selectable_reward_item3_4` TEXT COMMENT 'selectable_reward_item3_4',
    `reward_title_ext` TEXT COMMENT 'reward_title_ext',
    `abyss_rank` TEXT COMMENT 'abyss_rank',
    `target_type` TEXT COMMENT 'target_type',
    `reward_extend_inventory1` TEXT COMMENT 'reward_extend_inventory1',
    `finished_quest_cond3` TEXT COMMENT 'finished_quest_cond3',
    `inventory_item_name3` TEXT COMMENT 'inventory_item_name3',
    `reward_abyss_point4` TEXT COMMENT 'reward_abyss_point4',
    `quest_repeat_count` TEXT COMMENT 'quest_repeat_count',
    `quest_permitted_worlds` TEXT COMMENT 'quest_permitted_worlds',
    `areas` TEXT COMMENT 'areas',
    `can_report` TEXT COMMENT 'can_report',
    `can_repeat_reward` TEXT COMMENT 'can_repeat_reward',
    `reward_exp_ext` TEXT COMMENT 'reward_exp_ext',
    `equiped_item_name1` TEXT COMMENT 'equiped_item_name1',
    `equiped_item_name2` TEXT COMMENT 'equiped_item_name2',
    `equiped_item_name3` TEXT COMMENT 'equiped_item_name3',
    `equiped_item_name4` TEXT COMMENT 'equiped_item_name4',
    `equiped_item_name5` TEXT COMMENT 'equiped_item_name5',
    `reward_extend_stigma1` TEXT COMMENT 'reward_extend_stigma1',
    `unfinished_quest_cond2` TEXT COMMENT 'unfinished_quest_cond2',
    `unfinished_quest_cond3` TEXT COMMENT 'unfinished_quest_cond3',
    `unfinished_quest_cond4` TEXT COMMENT 'unfinished_quest_cond4',
    `unfinished_quest_cond5` TEXT COMMENT 'unfinished_quest_cond5',
    `unfinished_quest_cond6` TEXT COMMENT 'unfinished_quest_cond6',
    `noacquired_quest_cond2` TEXT COMMENT 'noacquired_quest_cond2',
    `noacquired_quest_cond3` TEXT COMMENT 'noacquired_quest_cond3',
    `noacquired_quest_cond4` TEXT COMMENT 'noacquired_quest_cond4',
    `noacquired_quest_cond5` TEXT COMMENT 'noacquired_quest_cond5',
    `noacquired_quest_cond6` TEXT COMMENT 'noacquired_quest_cond6',
    `combineskill` TEXT COMMENT 'combineskill',
    `combine_skillpoint` TEXT COMMENT 'combine_skillpoint',
    `selectable_reward_item3_5` TEXT COMMENT 'selectable_reward_item3_5',
    `selectable_reward_item4_1` TEXT COMMENT 'selectable_reward_item4_1',
    `selectable_reward_item4_2` TEXT COMMENT 'selectable_reward_item4_2',
    `selectable_reward_item4_3` TEXT COMMENT 'selectable_reward_item4_3',
    `selectable_reward_item4_4` TEXT COMMENT 'selectable_reward_item4_4',
    `selectable_reward_item4_5` TEXT COMMENT 'selectable_reward_item4_5',
    `reward_gold5` TEXT COMMENT 'reward_gold5',
    `selectable_reward_item5_1` TEXT COMMENT 'selectable_reward_item5_1',
    `selectable_reward_item5_2` TEXT COMMENT 'selectable_reward_item5_2',
    `selectable_reward_item5_3` TEXT COMMENT 'selectable_reward_item5_3',
    `selectable_reward_item5_4` TEXT COMMENT 'selectable_reward_item5_4',
    `selectable_reward_item5_5` TEXT COMMENT 'selectable_reward_item5_5',
    `reward_gold6` TEXT COMMENT 'reward_gold6',
    `selectable_reward_item6_1` TEXT COMMENT 'selectable_reward_item6_1',
    `selectable_reward_item_ext_1` TEXT COMMENT 'selectable_reward_item_ext_1',
    `selectable_reward_item_ext_2` TEXT COMMENT 'selectable_reward_item_ext_2',
    `finished_quest_cond4` TEXT COMMENT 'finished_quest_cond4',
    `finished_quest_cond5` TEXT COMMENT 'finished_quest_cond5',
    `finished_quest_cond6` TEXT COMMENT 'finished_quest_cond6',
    `selectable_reward_item2_6` TEXT COMMENT 'selectable_reward_item2_6',
    `selectable_reward_item_ext_3` TEXT COMMENT 'selectable_reward_item_ext_3',
    `selectable_reward_item_ext_4` TEXT COMMENT 'selectable_reward_item_ext_4',
    `selectable_reward_item_ext_5` TEXT COMMENT 'selectable_reward_item_ext_5',
    `selectable_reward_item_ext_6` TEXT COMMENT 'selectable_reward_item_ext_6',
    `selectable_reward_item_ext_7` TEXT COMMENT 'selectable_reward_item_ext_7',
    `selectable_reward_item_ext_8` TEXT COMMENT 'selectable_reward_item_ext_8',
    `selectable_reward_item_ext_9` TEXT COMMENT 'selectable_reward_item_ext_9',
    `selectable_reward_item_ext_10` TEXT COMMENT 'selectable_reward_item_ext_10',
    `selectable_reward_item_ext_11` TEXT COMMENT 'selectable_reward_item_ext_11',
    `reward_cp1` TEXT COMMENT 'reward_cp1',
    `reward_score1` TEXT COMMENT 'reward_score1',
    `reward_score_ext` TEXT COMMENT 'reward_score_ext',
    `reward_glory_point1` TEXT COMMENT 'reward_glory_point1',
    `recipe_name` TEXT COMMENT 'recipe_name',
    `npcfaction_name` TEXT COMMENT 'npcfaction_name',
    `package_permitted` TEXT COMMENT 'package_permitted',
    `pcguild_level` TEXT COMMENT 'pcguild_level',
    `max_count_limitedquest` TEXT COMMENT 'max_count_limitedquest',
    `count_recover_limitedquest` TEXT COMMENT 'count_recover_limitedquest',
    `finished_quest_count1` TEXT COMMENT 'finished_quest_count1',
    `finished_quest_count2` TEXT COMMENT 'finished_quest_count2',
    `reward_item_ext_2` TEXT COMMENT 'reward_item_ext_2',
    `reward_item_ext_3` TEXT COMMENT 'reward_item_ext_3',
    `reward_item_ext_4` TEXT COMMENT 'reward_item_ext_4',
    `reward_challenge_task1` TEXT COMMENT 'reward_challenge_task1',
    `reward_item5_1` TEXT COMMENT 'reward_item5_1',
    `acquired_quest_cond2` TEXT COMMENT 'acquired_quest_cond2',
    `selectable_reward_item2_7` TEXT COMMENT 'selectable_reward_item2_7',
    `selectable_reward_item2_8` TEXT COMMENT 'selectable_reward_item2_8',
    `selectable_reward_item2_9` TEXT COMMENT 'selectable_reward_item2_9',
    `selectable_reward_item2_10` TEXT COMMENT 'selectable_reward_item2_10',
    `selectable_reward_item2_11` TEXT COMMENT 'selectable_reward_item2_11',
    `selectable_reward_item2_12` TEXT COMMENT 'selectable_reward_item2_12',
    `selectable_reward_item2_13` TEXT COMMENT 'selectable_reward_item2_13',
    `selectable_reward_item1_14` TEXT COMMENT 'selectable_reward_item1_14',
    `selectable_reward_item1_15` TEXT COMMENT 'selectable_reward_item1_15',
    `quest_cooltime` TEXT COMMENT 'quest_cooltime',
    `reward_abyss_op_point1` TEXT COMMENT 'reward_abyss_op_point1',
    `reward_exp_boost1` TEXT COMMENT 'reward_exp_boost1',
    `check_item2_2` TEXT COMMENT 'check_item2_2',
    `mentor_quest_type` TEXT COMMENT 'mentor_quest_type',
    `selectable_reward_item3_6` TEXT COMMENT 'selectable_reward_item3_6',
    `burningreward_item` TEXT COMMENT 'burningreward_item',
    `burningreward_time_begin` TEXT COMMENT 'burningreward_time_begin',
    `burningreward_duration` TEXT COMMENT 'burningreward_duration',
    `reward_dp1` TEXT COMMENT 'reward_dp1'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'quest';

DROP TABLE IF EXISTS xmldb_suiyue.quest__fighter_selectable_reward__data;
CREATE TABLE xmldb_suiyue.quest__fighter_selectable_reward__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `fighter_selectable_item` VARCHAR(36) COMMENT 'fighter_selectable_item'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'quest__fighter_selectable_reward__data';

DROP TABLE IF EXISTS xmldb_suiyue.quest__knight_selectable_reward__data;
CREATE TABLE xmldb_suiyue.quest__knight_selectable_reward__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `knight_selectable_item` VARCHAR(36) COMMENT 'knight_selectable_item'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'quest__knight_selectable_reward__data';

DROP TABLE IF EXISTS xmldb_suiyue.quest__ranger_selectable_reward__data;
CREATE TABLE xmldb_suiyue.quest__ranger_selectable_reward__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `ranger_selectable_item` VARCHAR(37) COMMENT 'ranger_selectable_item'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'quest__ranger_selectable_reward__data';

DROP TABLE IF EXISTS xmldb_suiyue.quest__assassin_selectable_reward__data;
CREATE TABLE xmldb_suiyue.quest__assassin_selectable_reward__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `assassin_selectable_item` VARCHAR(36) COMMENT 'assassin_selectable_item'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'quest__assassin_selectable_reward__data';

DROP TABLE IF EXISTS xmldb_suiyue.quest__wizard_selectable_reward__data;
CREATE TABLE xmldb_suiyue.quest__wizard_selectable_reward__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `wizard_selectable_item` VARCHAR(36) COMMENT 'wizard_selectable_item'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'quest__wizard_selectable_reward__data';

DROP TABLE IF EXISTS xmldb_suiyue.quest__elementalist_selectable_reward__data;
CREATE TABLE xmldb_suiyue.quest__elementalist_selectable_reward__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `elementalist_selectable_item` VARCHAR(41) COMMENT 'elementalist_selectable_item'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'quest__elementalist_selectable_reward__data';

DROP TABLE IF EXISTS xmldb_suiyue.quest__priest_selectable_reward__data;
CREATE TABLE xmldb_suiyue.quest__priest_selectable_reward__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `priest_selectable_item` VARCHAR(37) COMMENT 'priest_selectable_item'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'quest__priest_selectable_reward__data';

DROP TABLE IF EXISTS xmldb_suiyue.quest__chanter_selectable_reward__data;
CREATE TABLE xmldb_suiyue.quest__chanter_selectable_reward__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `chanter_selectable_item` VARCHAR(40) COMMENT 'chanter_selectable_item'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'quest__chanter_selectable_reward__data';

DROP TABLE IF EXISTS xmldb_suiyue.quest__gunner_selectable_reward__data;
CREATE TABLE xmldb_suiyue.quest__gunner_selectable_reward__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `gunner_selectable_item` VARCHAR(36) COMMENT 'gunner_selectable_item'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'quest__gunner_selectable_reward__data';

DROP TABLE IF EXISTS xmldb_suiyue.quest__bard_selectable_reward__data;
CREATE TABLE xmldb_suiyue.quest__bard_selectable_reward__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `bard_selectable_item` VARCHAR(37) COMMENT 'bard_selectable_item'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'quest__bard_selectable_reward__data';

DROP TABLE IF EXISTS xmldb_suiyue.quest__rider_selectable_reward__data;
CREATE TABLE xmldb_suiyue.quest__rider_selectable_reward__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `rider_selectable_item` VARCHAR(36) COMMENT 'rider_selectable_item'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'quest__rider_selectable_reward__data';

