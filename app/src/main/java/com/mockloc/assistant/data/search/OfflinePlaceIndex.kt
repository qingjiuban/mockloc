package com.mockloc.assistant.data.search

import java.text.Normalizer

/**
 * 内置离线城市库：覆盖中国主要城市、省会、热门旅游/商务城市与世界主要城市。
 * 用于无网络、接口限流或地图不可用时的兜底搜索，保证核心选点流程可用。
 */
object OfflinePlaceIndex {

    private data class City(
        val name: String,
        val pinyin: String,
        val initial: String,
        val latitude: Double,
        val longitude: Double,
        val province: String = "",
        val country: String = "中国",
    )

    private val cities: List<City> = listOf(
        // 直辖市
        City("北京", "beijing", "bj", 39.9042, 116.4074, "北京市"),
        City("上海", "shanghai", "sh", 31.2304, 121.4737, "上海市"),
        City("天津", "tianjin", "tj", 39.0842, 117.2009, "天津市"),
        City("重庆", "chongqing", "cq", 29.5630, 106.5516, "重庆市"),
        // 省会与主要城市
        City("广州", "guangzhou", "gz", 23.1291, 113.2644, "广东省"),
        City("深圳", "shenzhen", "sz", 22.5431, 114.0579, "广东省"),
        City("东莞", "dongguan", "dg", 23.0207, 113.7518, "广东省"),
        City("佛山", "foshan", "fs", 23.0219, 113.1214, "广东省"),
        City("珠海", "zhuhai", "zh", 22.2707, 113.5767, "广东省"),
        City("中山", "zhongshan", "zs", 22.5176, 113.3926, "广东省"),
        City("惠州", "huizhou", "hz", 23.1115, 114.4162, "广东省"),
        City("汕头", "shantou", "st", 23.3541, 116.6820, "广东省"),
        City("杭州", "hangzhou", "hz", 30.2741, 120.1551, "浙江省"),
        City("宁波", "ningbo", "nb", 29.8683, 121.5440, "浙江省"),
        City("温州", "wenzhou", "wz", 27.9938, 120.6994, "浙江省"),
        City("嘉兴", "jiaxing", "jx", 30.7522, 120.7500, "浙江省"),
        City("绍兴", "shaoxing", "sx", 30.0023, 120.5810, "浙江省"),
        City("南京", "nanjing", "nj", 32.0603, 118.7969, "江苏省"),
        City("苏州", "suzhou", "sz", 31.2989, 120.5853, "江苏省"),
        City("无锡", "wuxi", "wx", 31.4912, 120.3119, "江苏省"),
        City("常州", "changzhou", "cz", 31.8107, 119.9740, "江苏省"),
        City("南通", "nantong", "nt", 31.9802, 120.8943, "江苏省"),
        City("徐州", "xuzhou", "xz", 34.2058, 117.2840, "江苏省"),
        City("成都", "chengdu", "cd", 30.5728, 104.0668, "四川省"),
        City("绵阳", "mianyang", "my", 31.4679, 104.6796, "四川省"),
        City("武汉", "wuhan", "wh", 30.5928, 114.3055, "湖北省"),
        City("宜昌", "yichang", "yc", 30.6919, 111.2865, "湖北省"),
        City("长沙", "changsha", "cs", 28.2282, 112.9388, "湖南省"),
        City("株洲", "zhuzhou", "zz", 27.8274, 113.1340, "湖南省"),
        City("西安", "xian", "xa", 34.3416, 108.9398, "陕西省"),
        City("咸阳", "xianyang", "xy", 34.3299, 108.7093, "陕西省"),
        City("郑州", "zhengzhou", "zz", 34.7466, 113.6254, "河南省"),
        City("洛阳", "luoyang", "ly", 34.6197, 112.4540, "河南省"),
        City("济南", "jinan", "jn", 36.6512, 117.1201, "山东省"),
        City("青岛", "qingdao", "qd", 36.0671, 120.3826, "山东省"),
        City("烟台", "yantai", "yt", 37.4638, 121.4479, "山东省"),
        City("潍坊", "weifang", "wf", 36.7069, 119.1618, "山东省"),
        City("沈阳", "shenyang", "sy", 41.8057, 123.4315, "辽宁省"),
        City("大连", "dalian", "dl", 38.9140, 121.6147, "辽宁省"),
        City("哈尔滨", "haerbin", "heb", 45.8038, 126.5349, "黑龙江省"),
        City("长春", "changchun", "cc", 43.8171, 125.3235, "吉林省"),
        City("石家庄", "shijiazhuang", "sjz", 38.0428, 114.5149, "河北省"),
        City("唐山", "tangshan", "ts", 39.6304, 118.1802, "河北省"),
        City("太原", "taiyuan", "ty", 37.8706, 112.5489, "山西省"),
        City("合肥", "hefei", "hf", 31.8206, 117.2272, "安徽省"),
        City("芜湖", "wuhu", "wh", 31.3526, 118.4331, "安徽省"),
        City("福州", "fuzhou", "fz", 26.0745, 119.2965, "福建省"),
        City("厦门", "xiamen", "xm", 24.4798, 118.0894, "福建省"),
        City("泉州", "quanzhou", "qz", 24.8741, 118.6757, "福建省"),
        City("南昌", "nanchang", "nc", 28.6820, 115.8579, "江西省"),
        City("赣州", "ganzhou", "gz", 25.8311, 114.9347, "江西省"),
        City("南宁", "nanning", "nn", 22.8170, 108.3665, "广西壮族自治区"),
        City("桂林", "guilin", "gl", 25.2736, 110.2900, "广西壮族自治区"),
        City("海口", "haikou", "hk", 20.0444, 110.1999, "海南省"),
        City("三亚", "sanya", "sy", 18.2528, 109.5119, "海南省"),
        City("昆明", "kunming", "km", 24.8801, 102.8329, "云南省"),
        City("大理", "dali", "dl", 25.6065, 100.2676, "云南省"),
        City("丽江", "lijiang", "lj", 26.8721, 100.2299, "云南省"),
        City("贵阳", "guiyang", "gy", 26.6470, 106.6302, "贵州省"),
        City("遵义", "zunyi", "zy", 27.7256, 106.9272, "贵州省"),
        City("兰州", "lanzhou", "lz", 36.0611, 103.8343, "甘肃省"),
        City("西宁", "xining", "xn", 36.6171, 101.7782, "青海省"),
        City("银川", "yinchuan", "yc", 38.4872, 106.2309, "宁夏回族自治区"),
        City("乌鲁木齐", "wulumuqi", "wlmq", 43.8256, 87.6168, "新疆维吾尔自治区"),
        City("拉萨", "lasa", "ls", 29.6520, 91.1721, "西藏自治区"),
        City("呼和浩特", "huhehaote", "hhht", 40.8414, 111.7519, "内蒙古自治区"),
        City("香港", "xianggang", "xg", 22.3193, 114.1694, "香港特别行政区"),
        City("澳门", "aomen", "am", 22.1987, 113.5439, "澳门特别行政区"),
        City("台北", "taibei", "tb", 25.0330, 121.5654, "台湾省"),
        City("高雄", "gaoxiong", "gx", 22.6273, 120.3014, "台湾省"),
        // 世界主要城市
        City("东京", "dongjing", "dj", 35.6762, 139.6503, "东京都", "日本"),
        City("大阪", "daban", "db", 34.6937, 135.5023, "大阪府", "日本"),
        City("首尔", "shouer", "se", 37.5665, 126.9780, "首尔特别市", "韩国"),
        City("新加坡", "xinjiapo", "xjp", 1.3521, 103.8198, "新加坡", "新加坡"),
        City("曼谷", "mangu", "mg", 13.7563, 100.5018, "曼谷", "泰国"),
        City("吉隆坡", "jilongpo", "jlp", 3.1390, 101.6869, "吉隆坡", "马来西亚"),
        City("迪拜", "dibai", "db", 25.2048, 55.2708, "迪拜", "阿联酋"),
        City("伦敦", "lundun", "ld", 51.5074, -0.1278, "英格兰", "英国"),
        City("巴黎", "bali", "bl", 48.8566, 2.3522, "法兰西岛", "法国"),
        City("柏林", "bolin", "bl", 52.5200, 13.4050, "柏林", "德国"),
        City("罗马", "luoma", "lm", 41.9028, 12.4964, "拉齐奥", "意大利"),
        City("马德里", "madeli", "mdl", 40.4168, -3.7038, "马德里", "西班牙"),
        City("莫斯科", "mosike", "msk", 55.7558, 37.6173, "莫斯科", "俄罗斯"),
        City("纽约", "niuyue", "ny", 40.7128, -74.0060, "纽约州", "美国"),
        City("洛杉矶", "luoshanji", "lsj", 34.0522, -118.2437, "加利福尼亚州", "美国"),
        City("旧金山", "jiujinshan", "jjs", 37.7749, -122.4194, "加利福尼亚州", "美国"),
        City("西雅图", "xiyatu", "xyt", 47.6062, -122.3321, "华盛顿州", "美国"),
        City("芝加哥", "zhijiage", "zjg", 41.8781, -87.6298, "伊利诺伊州", "美国"),
        City("多伦多", "duolunduo", "dld", 43.6532, -79.3832, "安大略省", "加拿大"),
        City("温哥华", "wengehua", "wgh", 49.2827, -123.1207, "不列颠哥伦比亚", "加拿大"),
        City("悉尼", "xini", "xn", -33.8688, 151.2093, "新南威尔士", "澳大利亚"),
        City("墨尔本", "moerben", "meb", -37.8136, 144.9631, "维多利亚", "澳大利亚"),
        City("奥克兰", "aokelan", "akl", -36.8485, 174.7633, "奥克兰", "新西兰"),
        City("圣保罗", "shengbaoluo", "sbl", -23.5505, -46.6333, "圣保罗州", "巴西"),
    )

    private val normalized: List<Pair<City, String>> = cities.map { city ->
        city to normalize("${city.name} ${city.pinyin} ${city.initial} ${city.province} ${city.country}")
    }

    fun search(query: String, limit: Int = 8): List<PlaceResult> {
        val q = normalize(query)
        if (q.isBlank()) return emptyList()

        val scored = normalized.mapNotNull { (city, haystack) ->
            val score = when {
                city.name == query.trim() -> 100
                city.pinyin == q -> 95
                city.initial == q -> 90
                haystack.startsWith(q) -> 80
                haystack.contains(q) -> 60
                city.name.contains(query.trim()) -> 70
                else -> 0
            }
            if (score > 0) city to score else null
        }.sortedByDescending { it.second }
            .take(limit)
            .map { (city, _) ->
                PlaceResult(
                    name = city.name,
                    displayName = listOf(city.country, city.province, city.name)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    latitude = city.latitude,
                    longitude = city.longitude,
                    source = PlaceSource.OFFLINE,
                )
            }
        return scored
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFKD)
            .replace(Regex("\\s+"), " ")
}
