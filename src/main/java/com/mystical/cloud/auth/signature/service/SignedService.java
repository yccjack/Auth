package com.mystical.cloud.auth.signature.service;

import com.mystical.cloud.auth.signature.annotation.SignedEntity;
import com.mystical.cloud.auth.signature.annotation.SignedIgnore;
import com.mystical.cloud.auth.signature.annotation.SignedMapping;
import com.mystical.cloud.auth.signature.exception.SignedException;
import com.mystical.cloud.auth.signature.util.RedisUtil;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.AnnotationUtils;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;


public abstract class SignedService {
    @Value("${signature.algorithm:HmacSHA1}")
    protected String ALGORITHM;
    @Value("${signature.time-diff-max:100}")
    protected long TIME_DIFF_MAX;
    @Autowired(required = false)
    protected RedisUtil redisUtil;
    protected static final String PREFIX = "atpapi:signature:";

    @Pointcut("@within(com.mystical.cloud.auth.signature.annotation.SignedMapping) || @annotation(com.mystical.cloud.auth.signature.annotation.SignedMapping) ")
    public void mapping() {
    }


    @Before("mapping() ")
    public void before(JoinPoint joinPoint) throws Exception {
        MethodSignature sign = (MethodSignature) joinPoint.getSignature();
        Method method = sign.getMethod();

        //  Get the annotations on the class or method, and get the parameter
        SignedMapping signedMapping = AnnotationUtils.findAnnotation(method, SignedMapping.class);
        if (signedMapping == null) {
            signedMapping = AnnotationUtils.findAnnotation(joinPoint.getTarget().getClass(), SignedMapping.class);
        }

        //??????????????????
        if (signedMapping == null || signedMapping.resubmit()) {
            return;
        }
        Class<?> clazz = signedMapping.value();

        //  If there are other implementation classes, prevent multiple executions here
        if (!this.getClass().equals(clazz)) {
            return;
        }

        Object[] args = joinPoint.getArgs();
        boolean paramHasSignedEntity = false;
        //??????SignedEntity ?????????
        if (args != null&& args.length>0) {
            for (Object obj : args) {
                if (obj==null){
                    continue;
                }
                SignedEntity signedEntity = AnnotationUtils.findAnnotation(obj.getClass(), SignedEntity.class);
                if (signedEntity != null) {
                    paramHasSignedEntity = true;
                    entry(obj);
                    break;
                }
            }
        }

        //???????????????????????????SignedEntity??????????????? request??????????????????.
        if (!paramHasSignedEntity) {
            signedValidate();
        }

    }

    protected abstract void entry(Object obj) throws Exception;

    /**
     * ????????????????????????
     *
     * @throws NoSuchAlgorithmException NoSuchAlgorithmException {@link NoSuchAlgorithmException}
     * @throws InvalidKeyException      InvalidKeyException {@link InvalidKeyException}
     */
    protected abstract void signedValidate() throws NoSuchAlgorithmException, InvalidKeyException;

    /**
     * Convert obj to map and verify that the field is not empty
     *
     * @param obj ??????????????????
     * @return ??????????????????key value??????
     * @throws IllegalAccessException InvalidKeyException {@link InvalidKeyException}
     */
    public Map<String, Object> object2Map(Object obj) throws IllegalAccessException {
        Map<String, Object> map = new HashMap<>();
        if (obj == null) {
            return map;
        }
        Class<?> clazz = obj.getClass();
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            //  If the field is not ignored, put it in the map
            //  If the field is null, throw an exception
            SignedIgnore signedIgnore = AnnotationUtils.findAnnotation(field, SignedIgnore.class);
            if (signedIgnore == null) {
                if (field.get(obj) == null) {
                    throw new SignedException.NullParam(field.getName());
                }
                map.put(field.getName(), field.get(obj));
            }
        }
        return map;
    }


    /**
     * Get the field value of obj through annotation
     *
     * @param obj            ????????????
     * @param annotationType ???????????????????????????
     * @return ?????? annotationType??? ??????
     * @throws IllegalAccessException InvalidKeyException {@link InvalidKeyException}
     */
    public final Object getParamByAnnotation(Object obj, Class<? extends Annotation> annotationType) throws IllegalAccessException {
        Class<?> clazz = obj.getClass();
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            Annotation annotation = AnnotationUtils.findAnnotation(field, annotationType);
            if (annotation != null) {
                return field.get(obj);
            }
        }
        return null;
    }


    /**
     * Get the appSecret by appId
     *
     * @param appId appId
     * @return ??????appId??????appSecret
     */
    public String getAppSecret(String appId) {
        Object appSecret = redisUtil.get(appId);
        if (appSecret == null)
            throw new SignedException.AppIdInvalid(appId);
        return appSecret.toString();
    }

    /**
     * If the time difference between the server and the client is too large, throw an exception
     *
     * @param timestamp ???????????????????????????
     */
    public void isTimeDiffLarge(long timestamp) {
        long diff = timestamp - System.currentTimeMillis() / 1000;
        if (Math.abs(diff) > TIME_DIFF_MAX) {
            throw new SignedException.TimestampError(diff + "");
        }
    }


    /**
     * Judge whether it is a replay attack by time stamp and nonce
     */
    public void isReplayAttack(String appId, long timestamp, Object nonce, String signature) {
        String key = PREFIX + appId + "_" + timestamp + "_" + nonce;
        Object obj = redisUtil.get(key);
        if (obj != null && signature.equals(obj.toString())) {
            throw new SignedException.ReplayAttack(appId, timestamp, nonce);
        } else {
            redisUtil.set(key, signature, TIME_DIFF_MAX);
        }
    }

}
