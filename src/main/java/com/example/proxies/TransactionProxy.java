package com.example.proxies;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aot.hint.annotation.Reflective;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.annotation.*;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class TransactionProxy {


    @Bean
    ApplicationRunner applicationRunner(DefaultCustomerService customerService) {
        return args -> {
            customerService.add();
            customerService.create();
        };
    }

    static boolean transactional(Object proxy) {
        var hasTransaction = new AtomicBoolean(false);

        var classes = new ArrayList<Class<?>>();
        classes.add(proxy.getClass());
        Collections.addAll(classes, proxy.getClass().getInterfaces());
        classes.forEach(clazz -> ReflectionUtils.doWithMethods(clazz, method -> {
            if (method.getAnnotation(MyTransactional.class) != null) {
                hasTransaction.set(true);
            }
        }));


        return hasTransaction.get();
    }

    @Bean
    DefaultCustomerService defaultCustomerService() {
        return new DefaultCustomerService();
    }


    @Bean
    MyTransactionalBeanProcessor myTransactionalBeanProcessor() {
        return new MyTransactionalBeanProcessor();
    }

    static class MyTransactionalBeanProcessor implements BeanPostProcessor {
        @Override
        public Object postProcessAfterInitialization(Object target, String beanName) throws BeansException {

            if (transactional(target)) {
                var proxyFactory = new ProxyFactory();
//                proxyFactory.setTargetClass(CustomerService.class);
                proxyFactory.setInterfaces(target.getClass().getInterfaces());

                proxyFactory.setTarget(target);
                proxyFactory.addAdvice((MethodInterceptor) methodInvocation -> {

                    Object[] arguments = methodInvocation.getArguments();
                    Method method = methodInvocation.getMethod();

                    try {
                        if (method.getAnnotation(MyTransactional.class) != null) {
                            System.out.println("starting transaction for : " + method.getName());
                        }

                        return method.invoke(target, arguments);
                    } finally {

                        if (method.getAnnotation(MyTransactional.class) != null) {
                            System.out.println("ending transaction for : " + method.getName());
                        }
                    }
                });
                return proxyFactory.getProxy(getClass().getClassLoader());
            }

            return BeanPostProcessor.super.postProcessAfterInitialization(target, beanName);
        }
    }


    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Documented
    @Reflective
    @interface MyTransactional {
    }

    interface CustomerService {

//        @MyTransactional
        void create();

        void add();
    }


    class DefaultCustomerService {

        @MyTransactional
        public void create() {
            System.out.println("create()");
        }


        public void add() {

            System.out.println("add()");
        }
    }

    void springProxy() {
        var target = new DefaultCustomerService();
        var proxyFactory = new ProxyFactory(target);
        proxyFactory.setInterfaces(target.getClass().getInterfaces());
        proxyFactory.setTarget(target);
        proxyFactory.addAdvice((MethodInterceptor) methodInvocation -> {

            Object[] arguments = methodInvocation.getArguments();
            Method method = methodInvocation.getMethod();

            try {
                if (method.getAnnotation(MyTransactional.class) != null) {
                    System.out.println("starting transaction for : " + method.getName());
                }

                return method.invoke(target, arguments);
            } finally {

                if (method.getAnnotation(MyTransactional.class) != null) {
                    System.out.println("ending transaction for : " + method.getName());
                }
            }
        });
        var proxyInstance = (CustomerService) proxyFactory.getProxy(getClass().getClassLoader());
        proxyInstance.create();
        proxyInstance.add();
    }

    void jdkProxy() {
        var target = new DefaultCustomerService();
        var proxyInstance = (CustomerService) Proxy.newProxyInstance(target.getClass().getClassLoader(), target.getClass().getInterfaces(),
                (proxy, method, args1) -> {
//                        System.out.println("calling " + method.getName() + " with args : [" + args1 + "]");


                    try {
                        if (method.getClass().getAnnotation(MyTransactional.class) != null) {
                            System.out.println("starting transaction for : " + method.getName());
                        }

                        return method.invoke(target, args1);
                    } finally {

                        if (method.getClass().getAnnotation(MyTransactional.class) != null) {
                            System.out.println("ending transaction for : " + method.getName());
                        }
                    }


                });

        proxyInstance.create();
        proxyInstance.add();
    }

}
