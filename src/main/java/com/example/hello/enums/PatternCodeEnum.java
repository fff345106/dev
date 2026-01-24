package com.example.hello.enums;

import java.util.*;

/**
 * 纹样代码枚举定义
 * 编码格式: 主类别(2)-子类别(2)-风格(2)-地区(2)-时期(2)-日期(6)-序列号(3)
 * 示例: AN-BD-TR-CN-QG-240615-001
 * 
 * 有子类别的主类别(AN/PL/PE): 主类别-子类别-风格-地区-时期-日期-序列号
 * 无子类别的主类别(LA/AB/OR/SY/CE/MY/OT): 主类别-风格1-风格2-地区-时期-日期-序列号
 */
public class PatternCodeEnum {

    // 有子类别的主类别集合
    public static final Set<String> CATEGORIES_WITH_SUB = Set.of("AN", "PL", "PE");

    /** 主类别代码 - 2位字母 */
    public enum MainCategory {
        AN("AN", "动物"),
        PL("PL", "植物"),
        PE("PE", "人物"),
        LA("LA", "风景"),
        AB("AB", "抽象"),
        OR("OR", "器物"),
        SY("SY", "符号"),
        CE("CE", "庆典"),
        MY("MY", "神话"),
        OT("OT", "其他");

        private final String code;
        private final String name;

        MainCategory(String code, String name) {
            this.code = code;
            this.name = name;
        }

        public String getCode() { return code; }
        public String getName() { return name; }

        public static boolean isValid(String code) {
            return Arrays.stream(values()).anyMatch(e -> e.code.equals(code));
        }

        public static MainCategory fromCode(String code) {
            return Arrays.stream(values())
                    .filter(e -> e.code.equals(code))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("无效的主类别代码: " + code));
        }

        public boolean hasSubCategory() {
            return CATEGORIES_WITH_SUB.contains(this.code);
        }
    }

    /** 动物子类别代码 */
    public enum AnimalSubCategory {
        BD("BD", "鸟类"),
        FS("FS", "鱼类"),
        IN("IN", "昆虫"),
        MA("MA", "哺乳动物"),
        MY("MY", "神话动物"),
        RP("RP", "爬行动物"),
        OT("OT", "其他动物");

        private final String code;
        private final String name;

        AnimalSubCategory(String code, String name) {
            this.code = code;
            this.name = name;
        }

        public String getCode() { return code; }
        public String getName() { return name; }

        public static boolean isValid(String code) {
            return Arrays.stream(values()).anyMatch(e -> e.code.equals(code));
        }
    }

    /** 植物子类别代码 */
    public enum PlantSubCategory {
        FL("FL", "花卉"),
        TR("TR", "树木"),
        FR("FR", "果实"),
        GR("GR", "谷物"),
        LV("LV", "叶子"),
        OT("OT", "其他植物");

        private final String code;
        private final String name;

        PlantSubCategory(String code, String name) {
            this.code = code;
            this.name = name;
        }

        public String getCode() { return code; }
        public String getName() { return name; }

        public static boolean isValid(String code) {
            return Arrays.stream(values()).anyMatch(e -> e.code.equals(code));
        }
    }

    /** 人物子类别代码 */
    public enum PersonSubCategory {
        MU("MU", "男性"),
        FE("FE", "女性"),
        CH("CH", "儿童"),
        EL("EL", "老人"),
        CE("CE", "名人"),
        OT("OT", "其他人物");

        private final String code;
        private final String name;

        PersonSubCategory(String code, String name) {
            this.code = code;
            this.name = name;
        }

        public String getCode() { return code; }
        public String getName() { return name; }

        public static boolean isValid(String code) {
            return Arrays.stream(values()).anyMatch(e -> e.code.equals(code));
        }
    }

    /** 风格代码 - 2位字母 */
    public enum Style {
        TR("TR", "传统"),
        MO("MO", "现代"),
        FO("FO", "民间"),
        ET("ET", "民族"),
        GE("GE", "几何"),
        RE("RE", "写实"),
        DE("DE", "装饰"),
        MI("MI", "混合"),
        OT("OT", "其他风格");

        private final String code;
        private final String name;

        Style(String code, String name) {
            this.code = code;
            this.name = name;
        }

        public String getCode() { return code; }
        public String getName() { return name; }

        public static boolean isValid(String code) {
            return Arrays.stream(values()).anyMatch(e -> e.code.equals(code));
        }

        public static Style fromCode(String code) {
            return Arrays.stream(values())
                    .filter(e -> e.code.equals(code))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("无效的风格代码: " + code));
        }
    }

    /** 地区代码 - 2位字母 */
    public enum Region {
        CN("CN", "中国"),
        BJ("BJ", "北京"),
        TJ("TJ", "天津"),
        HB("HB", "河北"),
        SX("SX", "山西"),
        SD("SD", "山东"),
        JS("JS", "江苏"),
        ZJ("ZJ", "浙江"),
        AH("AH", "安徽"),
        FJ("FJ", "福建"),
        GD("GD", "广东"),
        SC("SC", "四川"),
        YN("YN", "云南"),
        OT("OT", "其他省份");

        private final String code;
        private final String name;

        Region(String code, String name) {
            this.code = code;
            this.name = name;
        }

        public String getCode() { return code; }
        public String getName() { return name; }

        public static boolean isValid(String code) {
            return Arrays.stream(values()).anyMatch(e -> e.code.equals(code));
        }

        public static Region fromCode(String code) {
            return Arrays.stream(values())
                    .filter(e -> e.code.equals(code))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("无效的地区代码: " + code));
        }
    }

    /** 时期代码 - 2位字母 */
    public enum Period {
        XS("XS", "先秦"),
        QG("QG", "秦汉"),
        WS("WS", "魏晋"),
        TG("TG", "隋唐"),
        SG("SG", "宋元"),
        MG("MG", "明清"),
        MJ("MJ", "民国"),
        XD("XD", "现代"),
        OT("OT", "其他时期");

        private final String code;
        private final String name;

        Period(String code, String name) {
            this.code = code;
            this.name = name;
        }

        public String getCode() { return code; }
        public String getName() { return name; }

        public static boolean isValid(String code) {
            return Arrays.stream(values()).anyMatch(e -> e.code.equals(code));
        }

        public static Period fromCode(String code) {
            return Arrays.stream(values())
                    .filter(e -> e.code.equals(code))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("无效的时期代码: " + code));
        }
    }

    /**
     * 验证子类别是否与主类别匹配
     */
    public static boolean isValidSubCategory(String mainCategory, String subCategory) {
        if (!CATEGORIES_WITH_SUB.contains(mainCategory)) {
            // 无子类别的主类别，子类别位置使用风格代码
            return Style.isValid(subCategory);
        }
        
        return switch (mainCategory) {
            case "AN" -> AnimalSubCategory.isValid(subCategory);
            case "PL" -> PlantSubCategory.isValid(subCategory);
            case "PE" -> PersonSubCategory.isValid(subCategory);
            default -> false;
        };
    }

    /**
     * 获取主类别对应的所有子类别
     */
    public static List<Map<String, String>> getSubCategories(String mainCategory) {
        List<Map<String, String>> result = new ArrayList<>();
        
        if (!CATEGORIES_WITH_SUB.contains(mainCategory)) {
            // 无子类别的主类别，返回风格列表
            for (Style s : Style.values()) {
                result.add(Map.of("code", s.getCode(), "name", s.getName()));
            }
            return result;
        }

        switch (mainCategory) {
            case "AN" -> {
                for (AnimalSubCategory s : AnimalSubCategory.values()) {
                    result.add(Map.of("code", s.getCode(), "name", s.getName()));
                }
            }
            case "PL" -> {
                for (PlantSubCategory s : PlantSubCategory.values()) {
                    result.add(Map.of("code", s.getCode(), "name", s.getName()));
                }
            }
            case "PE" -> {
                for (PersonSubCategory s : PersonSubCategory.values()) {
                    result.add(Map.of("code", s.getCode(), "name", s.getName()));
                }
            }
        }
        return result;
    }
}
