package net.amzscout.demo;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutionException;

@Component
public class ThrottleInterceptor extends HandlerInterceptorAdapter {

    private Logger logger = LoggerFactory.getLogger(ThrottleInterceptor.class);

    private LoadingCache<String, RateLimiter> rate_limit_cache;

    public ThrottleInterceptor(Environment env) {
        super();

        int max_requests = env.getRequiredProperty("throttled.max_requests", Integer.class);
        int expire_after_minutes = env.getRequiredProperty("throttled.expire_after_minutes", Integer.class);

        rate_limit_cache = CacheBuilder.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(expire_after_minutes))
                .build(new CacheLoader<>() {
                    public RateLimiter load(String key) {
                        return new RateLimiter(max_requests, expire_after_minutes);
                    }
                });
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) throws Exception {
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        Throttled throttled = handlerMethod.getMethod().getAnnotation(Throttled.class);

        if (throttled == null) {
            return super.preHandle(req, resp, handler);
        }

        try {
            String ip = getIP(req);
            RateLimiter limiter = rate_limit_cache.get(ip);

            if (limiter.isAvailable()) {
                return super.preHandle(req, resp, handler);
            }

        } catch (ExecutionException e) {
            logger.error(e.getMessage(), e);
        }

        // TOO_MANY_REQUESTS would be better
        resp.setStatus(HttpStatus.BAD_GATEWAY.value());
        resp.getWriter().write("Nope");
        return false;
    }


    private String getIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }


    private class RateLimiter {

        private long max_requests;
        private long expire_after_minutes;
        private Queue<Long> queue;

        RateLimiter(int max_requests, long expire_after_minutes) {
            this.queue = new LinkedList<>();
            this.max_requests = max_requests;
            this.expire_after_minutes = expire_after_minutes;
        }

        synchronized boolean isAvailable() {
            long now = System.currentTimeMillis();

            if (queue.size() < max_requests || queue.size() == 0) {
                queue.add(now);
                return true;
            }

            Long head = queue.poll();
            queue.add(now);
            long goes_by = (now - head) / 1000 / 60;

            return goes_by >= expire_after_minutes;
        }
    }

}
