package com.hmdp;

import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private RedisIdWorker redisIdWorker;

    private ExecutorService es = Executors.newFixedThreadPool(300);

    @Test
    public void IDWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable runnable = () -> {
           for (int i = 0; i < 100; i++) {
               long id = redisIdWorker.nextId("order");
               System.out.println(id);
           }
           latch.countDown();
       };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
           es.submit(runnable);
       }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println(end - begin);
    }
}
