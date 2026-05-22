package com.example.rag.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.util.Map;

@Slf4j
@Component
public class CarPriceCalculator {

    private static final Map<String, double[]> CAR_PRICES = new java.util.HashMap<>();
    static {
        CAR_PRICES.put("秦PLUS DM-i", new double[]{7.98, 9.98, 11.58, 12.58, 14.58});
        CAR_PRICES.put("秦PLUS", new double[]{7.98, 9.98, 11.58, 12.58, 14.58});
        CAR_PRICES.put("汉 DM-i", new double[]{16.98, 21.98});
        CAR_PRICES.put("汉 EV", new double[]{17.98, 20.98, 29.98});
        CAR_PRICES.put("汉", new double[]{16.98, 21.98});
        CAR_PRICES.put("唐 DM-i", new double[]{17.98, 21.98});
        CAR_PRICES.put("唐 EV", new double[]{22.98, 28.98, 31.98});
        CAR_PRICES.put("唐", new double[]{17.98, 21.98});
        CAR_PRICES.put("宋PLUS DM-i", new double[]{12.98, 14.58, 15.98, 16.98});
        CAR_PRICES.put("宋PLUS", new double[]{12.98, 14.58, 15.98, 16.98});
        CAR_PRICES.put("海豹", new double[]{17.98, 20.28, 23.98, 27.98});
        CAR_PRICES.put("海鸥", new double[]{6.98, 7.58, 8.98});
        CAR_PRICES.put("元PLUS", new double[]{13.58, 14.58, 15.98, 16.98});
        CAR_PRICES.put("卡罗拉", new double[]{11.68, 12.78});
        CAR_PRICES.put("卡罗拉双擎", new double[]{13.18, 14.18, 15.58});
        CAR_PRICES.put("凯美瑞", new double[]{17.98, 19.98});
        CAR_PRICES.put("凯美瑞双擎", new double[]{21.98, 23.48, 26.98});
        CAR_PRICES.put("RAV4", new double[]{17.68, 19.58});
        CAR_PRICES.put("RAV4双擎", new double[]{22.58, 24.18, 26.38});
        CAR_PRICES.put("RAV4荣放", new double[]{17.68, 19.58});
        CAR_PRICES.put("汉兰达", new double[]{26.88, 28.88, 31.88, 34.88});
        CAR_PRICES.put("亚洲龙", new double[]{19.98, 21.78});
        CAR_PRICES.put("亚洲龙双擎", new double[]{23.98, 25.78, 27.98});
        CAR_PRICES.put("普拉多", new double[]{45.98, 48.98, 51.98, 54.98});
        CAR_PRICES.put("思域", new double[]{12.99, 14.49});
        CAR_PRICES.put("思域混动", new double[]{15.39, 18.79});
        CAR_PRICES.put("雅阁", new double[]{17.98, 19.78});
        CAR_PRICES.put("雅阁混动", new double[]{21.98, 25.98});
        CAR_PRICES.put("CR-V", new double[]{18.59, 20.19});
        CAR_PRICES.put("CR-V混动", new double[]{20.99, 24.99});
        CAR_PRICES.put("飞度", new double[]{8.18, 8.88, 9.68, 10.88});
        CAR_PRICES.put("型格", new double[]{12.99, 14.29, 15.79});
        CAR_PRICES.put("型格混动", new double[]{16.69});
        CAR_PRICES.put("哈弗H6", new double[]{9.89, 11.59, 13.69});
        CAR_PRICES.put("哈弗H6混动", new double[]{14.98, 15.70});
        CAR_PRICES.put("坦克300", new double[]{19.98, 21.58, 26.18, 30.28});
        CAR_PRICES.put("蓝山", new double[]{27.38, 30.88, 32.68});
        CAR_PRICES.put("欧拉好猫", new double[]{10.78, 12.38, 13.98, 14.98});
    }

    private static final DecimalFormat DF = new DecimalFormat("#.##");

    @Tool(description = "计算某款车型所有配置的落地价格，包含购置税、保险、上牌费等")
    public String calculateOnRoadPrice(
            @ToolParam(description = "车型名称，如秦PLUS、凯美瑞、汉兰达等") String carModel) {

        double[] prices = findPrices(carModel);
        if (prices == null) {
            return "抱歉，系统中暂未收录【" + carModel + "】的价格信息。建议尝试其他车型名称，或联系销售顾问获取最新报价。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【").append(carModel).append("】落地价格明细：\n\n");

        String[] configNames = generateConfigNames(carModel, prices.length);

        for (int i = 0; i < prices.length; i++) {
            double guidePrice = prices[i];
            double purchaseTax = guidePrice / 11.3;
            double insurance = 4000 + guidePrice * 0.01;
            double registrationFee = 500;
            double total = guidePrice + purchaseTax + insurance + registrationFee;

            String configName = configNames.length > i ? configNames[i] : "配置" + (i + 1);
            sb.append("📋 ").append(configName).append("：\n");
            sb.append("   指导价：").append(guidePrice).append("万\n");
            sb.append("   购置税：约").append(DF.format(purchaseTax)).append("万\n");
            sb.append("   保险（首年）：约").append(DF.format(insurance / 10000)).append("万\n");
            sb.append("   上牌费：500元\n");
            sb.append("   💰 落地总价：约").append(DF.format(total)).append("万\n\n");
        }

        sb.append("注：以上为估算价格，实际以当地4S店报价为准。新能源车型免征购置税，落地价更低。");

        log.info("CarPriceCalculator: calculated on-road price for {}", carModel);
        return sb.toString();
    }

    @Tool(description = "计算分期购车的月供金额，含不同首付比例和贷款年限的组合方案")
    public String calculateMonthlyPayment(
            @ToolParam(description = "车型名称") String carModel,
            @ToolParam(description = "首付比例，如0.3表示30%首付") double downPaymentRatio,
            @ToolParam(description = "贷款年限（1-5年）") int years) {

        double[] prices = findPrices(carModel);
        if (prices == null) {
            return "抱歉，系统中暂未收录【" + carModel + "】的价格信息。";
        }

        double guidePrice = prices[0];
        double totalPrice = guidePrice * 10000;
        double downPayment = totalPrice * downPaymentRatio;
        double loanAmount = totalPrice - downPayment;
        int totalMonths = years * 12;

        double annualRate = downPaymentRatio >= 0.3 ? 0.0299 : 0.0499;
        double monthlyRate = annualRate / 12;

        double monthlyPayment;
        if (monthlyRate == 0) {
            monthlyPayment = loanAmount / totalMonths;
        } else {
            monthlyPayment = loanAmount * monthlyRate * Math.pow(1 + monthlyRate, totalMonths)
                    / (Math.pow(1 + monthlyRate, totalMonths) - 1);
        }

        double totalInterest = monthlyPayment * totalMonths - loanAmount;

        StringBuilder sb = new StringBuilder();
        sb.append("【").append(carModel).append("】分期方案：\n\n");
        sb.append("📌 指导价：").append(guidePrice).append("万\n");
        sb.append("💰 首付").append((int)(downPaymentRatio * 100)).append("%：约").append(DF.format(downPayment / 10000)).append("万\n");
        sb.append("🏦 贷款金额：约").append(DF.format(loanAmount / 10000)).append("万\n");
        sb.append("📆 贷款期限：").append(years).append("年（").append(totalMonths).append("期）\n");
        sb.append("📊 年利率：").append(DF.format(annualRate * 100)).append("%\n\n");
        sb.append("💳 月供：").append(DF.format(monthlyPayment)).append("元/月\n");
        sb.append("📋 总还款：约").append(DF.format(monthlyPayment * totalMonths / 10000)).append("万\n");
        sb.append("💸 总利息：约").append(DF.format(totalInterest / 10000)).append("万\n\n");

        sb.append("💡 换成更低首付或其他年限？回复'帮我算算首付20%分3年'试试");

        log.info("CarPriceCalculator: calculated monthly payment for {}, ratio={}, years={}", carModel, downPaymentRatio, years);
        return sb.toString();
    }

    private double[] findPrices(String model) {
        for (Map.Entry<String, double[]> entry : CAR_PRICES.entrySet()) {
            if (entry.getKey().contains(model) || model.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        String lowerModel = model.toLowerCase().replace(" ", "");
        for (Map.Entry<String, double[]> entry : CAR_PRICES.entrySet()) {
            if (entry.getKey().toLowerCase().replace(" ", "").contains(lowerModel)
                    || lowerModel.contains(entry.getKey().toLowerCase().replace(" ", ""))) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String[] generateConfigNames(String model, int count) {
        if (model.contains("秦PLUS")) return new String[]{"55km领先型", "55km超越型", "120km领先型", "120km超越型", "120km卓越型"};
        if (model.contains("汉") && model.contains("DM")) return new String[]{"121km荣耀版", "200km旗舰版"};
        if (model.contains("汉") && model.contains("EV")) return new String[]{"506km荣耀版", "605km冠军版", "四驱旗舰版"};
        if (model.contains("唐") && model.contains("DM")) return new String[]{"112km尊贵型", "200km尊享型"};
        if (model.contains("唐") && model.contains("EV")) return new String[]{"600km尊享型", "730km旗舰型", "四驱旗舰版"};
        if (model.contains("宋PLUS")) return new String[]{"51km尊贵型", "110km旗舰型", "110km旗舰PLUS", "150km旗舰版"};
        if (model.contains("海豹")) return new String[]{"550km精英型", "550km尊贵型", "700km性能型", "650km四驱版"};
        if (model.contains("海鸥")) return new String[]{"305km活力版", "305km自由版", "405km飞翔版"};
        if (model.contains("元PLUS")) return new String[]{"430km豪华型", "430km尊贵型", "510km尊荣型", "510km旗舰型"};
        if (model.contains("卡罗拉")) return new String[]{"1.2T先锋版", "1.2T精英版", "双擎先锋版", "双擎精英版", "双擎旗舰版"};
        if (model.contains("凯美瑞")) return new String[]{"2.0E精英版", "2.0G豪华版", "2.5HG豪华版", "2.5HS锋尚版", "2.5HQ旗舰版"};
        if (model.contains("RAV4")) return new String[]{"2.0L都市版", "2.0L风尚版", "双擎精英版", "双擎豪华四驱", "双擎旗舰四驱"};
        if (model.contains("汉兰达")) return new String[]{"精英版5座", "豪华版7座", "尊贵版7座", "至尊版7座"};
        if (model.contains("亚洲龙")) return new String[]{"2.0L进取版", "2.0L豪华版", "双擎豪华版", "双擎尊贵版", "双擎旗舰版"};
        if (model.contains("普拉多")) return new String[]{"全能版", "全能进阶版", "征服版", "旗舰版"};
        if (model.contains("哈弗H6")) return new String[]{"1.5T都市版", "1.5T冠军版", "2.0T四驱版", "Hi4畅行版", "Hi4旗舰版"};
        if (model.contains("坦克300")) return new String[]{"探索者", "征服者", "铁骑02", "边境版"};
        if (model.contains("蓝山")) return new String[]{"两驱长续航版", "四驱超长续航版", "四驱智驾版"};
        if (model.contains("好猫")) return new String[]{"401km舒享版", "401km尊贵版", "501km尊贵版", "501km旗舰版"};
        if (model.contains("思域")) return new String[]{"1.5T劲势版", "1.5T燃动版", "e:HEV锐利版", "e:HEV锐尊版"};
        if (model.contains("雅阁")) return new String[]{"1.5T舒适版", "1.5T豪华版", "e:HEV锐领版", "e:HEV锐尊版"};
        if (model.contains("CR-V")) return new String[]{"1.5T活力版", "1.5T风尚版", "e:HEV智尚版", "e:HEV智尊版"};
        if (model.contains("飞度")) return new String[]{"潮启版", "潮享版", "潮跑版", "潮越版"};
        if (model.contains("型格")) return new String[]{"1.5T手动版", "1.5T科技版", "1.5T幻夜版", "e:HEV锐·尊享版"};

        String[] names = new String[count];
        for (int i = 0; i < count; i++) {
            names[i] = "配置" + (i + 1);
        }
        return names;
    }
}
