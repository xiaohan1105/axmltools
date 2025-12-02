package red.jiuzhou.util;

import java.util.concurrent.atomic.LongAdder;
/**
 * @className: red.jiuzhou.util.CounterUtil.java
 * @description: 计数器工具类
 * @author: yanxq
 * @date:  2025-03-28 14:33
 * @version V1.0
 */
public class CounterUtil {
    // 使用 LongAdder 作为计数器
    private final LongAdder counter = new LongAdder();

    /**
     * 增加计数器，每次增1。
     */
    public void increment() {
        counter.increment();
    }

    /**
     * 获取当前计数值。
     * @return 当前计数值
     */
    public long getCount() {
        return counter.sum();
    }

    /**
     * 重置计数器。
     */
    public void reset() {
        counter.reset();
    }

    /**
     * 获取并重置计数器。
     * @return 计数器在重置之前的值
     */
    public long sumThenReset() {
        return counter.sumThenReset();
    }
}