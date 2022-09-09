package com.atguigu.gmall.common.util;

import com.atguigu.gmall.model.activity.ActivityRule;
import com.atguigu.gmall.model.activity.CouponInfo;
import com.atguigu.gmall.model.enums.ActivityType;
import com.atguigu.gmall.model.enums.CouponType;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderDetailVo;
import com.atguigu.gmall.model.order.OrderInfo;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author mqx
 * @date 2021-1-15 19:58:38
 */
public class OrderUtil {

    /**
     * 计算购物项分摊的优惠减少金额
     * 打折：按折扣分担
     * 现金：按比例分摊
     * @param orderInfo
     * @return
     */
    public static Map<String, BigDecimal> computeOrderDetailPayAmount(OrderInfo orderInfo) {
        //  创建一个对象
        Map<String, BigDecimal> skuIdToReduceAmountMap = new HashMap<>();
        //  获取分组订单明细集合
        List<OrderDetailVo> orderDetailVoList = orderInfo.getOrderDetailVoList();
        //  判断是否为空
        if(!CollectionUtils.isEmpty(orderDetailVoList)) {
            //  循环遍历
            for(OrderDetailVo orderDetailVo : orderDetailVoList) {
                //  获取活动规则
                ActivityRule activityRule = orderDetailVo.getActivityRule();
                //  获取活动规则对应的明细
                List<OrderDetail> orderDetailList = orderDetailVo.getOrderDetailList();
                //  判断活动规则不为空
                if(null != activityRule) {
                    // 优惠金额， 按比例分摊
                    BigDecimal reduceAmount = activityRule.getReduceAmount();
                    if(orderDetailList.size() == 1) {
                        skuIdToReduceAmountMap.put("activity:"+orderDetailList.get(0).getSkuId(), reduceAmount);
                    } else {
                        // 总金额
                        BigDecimal originalTotalAmount = new BigDecimal(0);
                        for(OrderDetail orderDetail : orderDetailList) {
                            BigDecimal skuTotalAmount = orderDetail.getOrderPrice().multiply(new BigDecimal(orderDetail.getSkuNum()));
                            //  原始金额
                            originalTotalAmount = originalTotalAmount.add(skuTotalAmount);
                        }
                        // 记录除最后一项是所有分摊金额， 最后一项 =总的 - skuPartReduceAmount
                        BigDecimal skuPartReduceAmount = new BigDecimal(0);
                        //  满减
                        if (activityRule.getActivityType().equals(ActivityType.FULL_REDUCTION.name())) {
                            for(int i=0, len=orderDetailList.size(); i<len; i++) {
                                OrderDetail orderDetail = orderDetailList.get(i);
                                //  len = 2 0 < 1
                                if(i < len -1) {
                                    //  当前订单明细金额
                                    BigDecimal skuTotalAmount = orderDetail.getOrderPrice().multiply(new BigDecimal(orderDetail.getSkuNum()));
                                    //sku分摊金额  四舍五入
                                    BigDecimal skuReduceAmount = skuTotalAmount.divide(originalTotalAmount, 2, RoundingMode.HALF_UP).multiply(reduceAmount);
                                    //  skuId 对应活动的促销金额占比
                                    skuIdToReduceAmountMap.put("activity:"+orderDetail.getSkuId(), skuReduceAmount);
                                    //  分摊金额 n-1 的分摊总金额  originalTotalAmount = skuReduceAmount
                                    skuPartReduceAmount = skuPartReduceAmount.add(skuReduceAmount);
                                } else {
                                    //  计算最后一项：
                                    BigDecimal skuReduceAmount = reduceAmount.subtract(skuPartReduceAmount);
                                    skuIdToReduceAmountMap.put("activity:"+orderDetail.getSkuId(), skuReduceAmount);
                                }
                            }
                        } else {    //  折扣
                            for(int i=0, len=orderDetailList.size(); i<len; i++) {
                                OrderDetail orderDetail = orderDetailList.get(i);
                                if(i < len -1) {
                                    BigDecimal skuTotalAmount = orderDetail.getOrderPrice().multiply(new BigDecimal(orderDetail.getSkuNum()));

                                    //sku分摊金额
                                    BigDecimal skuDiscountTotalAmount = skuTotalAmount.multiply(activityRule.getBenefitDiscount().divide(new BigDecimal("10")));
                                    BigDecimal skuReduceAmount = skuTotalAmount.subtract(skuDiscountTotalAmount);
                                    skuIdToReduceAmountMap.put("activity:"+orderDetail.getSkuId(), skuReduceAmount);

                                    skuPartReduceAmount = skuPartReduceAmount.add(skuReduceAmount);
                                } else {
                                    BigDecimal skuReduceAmount = reduceAmount.subtract(skuPartReduceAmount);
                                    skuIdToReduceAmountMap.put("activity:"+orderDetail.getSkuId(), skuReduceAmount);
                                }
                            }
                        }
                    }
                }
            }
        }

        //  购物券处理
        CouponInfo couponInfo = orderInfo.getCouponInfo();
        if(null != couponInfo) {
            //  sku对应的订单明细
            Map<Long, OrderDetail> skuIdToOrderDetailMap = new HashMap<>();
            for (OrderDetail orderDetail : orderInfo.getOrderDetailList()) {
                skuIdToOrderDetailMap.put(orderDetail.getSkuId(), orderDetail);
            }
            //购物券对应的skuId列表
            List<Long> skuIdList = couponInfo.getSkuIdList();
            //购物券总金额
            BigDecimal reduceAmount = couponInfo.getReduceAmount();
            if(skuIdList.size() == 1) {
                //  coupon:skuId  reduceAmount
                skuIdToReduceAmountMap.put("coupon:"+skuIdList.get(0), reduceAmount);
            } else {
                //  总金额
                BigDecimal originalTotalAmount = new BigDecimal(0);
                for (Long skuId : skuIdList) {
                    OrderDetail orderDetail = skuIdToOrderDetailMap.get(skuId);
                    BigDecimal skuTotalAmount = orderDetail.getOrderPrice().multiply(new BigDecimal(orderDetail.getSkuNum()));
                    //  计算原始的金额
                    originalTotalAmount = originalTotalAmount.add(skuTotalAmount);
                }
                // 记录除最后一项是所有分摊金额， 最后一项=总的 - skuPartReduceAmount
                BigDecimal skuPartReduceAmount = new BigDecimal(0);
                //  1 现金券 3 满减券
                if (couponInfo.getCouponType().equals(CouponType.CASH.name()) || couponInfo.getCouponType().equals(CouponType.FULL_REDUCTION.name())) {
                    for(int i=0, len=skuIdList.size(); i<len; i++) {
                        OrderDetail orderDetail = skuIdToOrderDetailMap.get(skuIdList.get(i));
                        if(i < len -1) {
                            BigDecimal skuTotalAmount = orderDetail.getOrderPrice().multiply(new BigDecimal(orderDetail.getSkuNum()));
                            //sku分摊金额
                            BigDecimal skuReduceAmount = skuTotalAmount.divide(originalTotalAmount, 2, RoundingMode.HALF_UP).multiply(reduceAmount);
                            skuIdToReduceAmountMap.put("coupon:"+orderDetail.getSkuId(), skuReduceAmount);

                            skuPartReduceAmount = skuPartReduceAmount.add(skuReduceAmount);
                        } else {
                            BigDecimal skuReduceAmount = reduceAmount.subtract(skuPartReduceAmount);
                            skuIdToReduceAmountMap.put("coupon:"+orderDetail.getSkuId(), skuReduceAmount);
                        }
                    }
                } else {    //  2 折扣券 4 满件打折券
                    for(int i=0, len=skuIdList.size(); i<len; i++) {
                        OrderDetail orderDetail = skuIdToOrderDetailMap.get(skuIdList.get(i));
                        if(i < len -1) {
                            BigDecimal skuTotalAmount = orderDetail.getOrderPrice().multiply(new BigDecimal(orderDetail.getSkuNum()));
                            BigDecimal skuDiscountTotalAmount = skuTotalAmount.multiply(couponInfo.getBenefitDiscount().divide(new BigDecimal("10")));
                            BigDecimal skuReduceAmount = skuTotalAmount.subtract(skuDiscountTotalAmount);
                            //sku分摊金额
                            skuIdToReduceAmountMap.put("coupon:"+orderDetail.getSkuId(), skuReduceAmount);

                            skuPartReduceAmount = skuPartReduceAmount.add(skuReduceAmount);
                        } else {
                            BigDecimal skuReduceAmount = reduceAmount.subtract(skuPartReduceAmount);
                            skuIdToReduceAmountMap.put("coupon:"+orderDetail.getSkuId(), skuReduceAmount);
                        }
                    }
                }
            }
        }
        return skuIdToReduceAmountMap;
    }
}
