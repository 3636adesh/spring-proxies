package com.example.proxies;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.aot.hint.annotation.Reflective;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.annotation.*;

import org.aopalliance.intercept.MethodInterceptor;

import java.lang.annotation.*;
import java.lang.reflect.Method;

/**
 * Demonstrates the use of CGLIB-based proxying in Spring for transaction management and other cross-cutting concerns.
 *
 * <p>In Spring, proxies are used to add additional behavior around method invocations, such as transaction
 * management, caching, and security. When CGLIB is employed, the proxy is created by subclassing the target class,
 * enabling method interception without requiring interfaces. This is particularly useful when:
 * <ul>
 *     <li>The target class does not implement any interfaces.</li>
 *     <li>Public methods of the target class require additional behavior (e.g., transactional boundaries).</li>
 * </ul>
 *
 * <h3>How CGLIB Proxying Works:</h3>
 * <p>
 * CGLIB (Code Generation Library) creates a dynamic proxy by generating a subclass of the target class at runtime.
 * This subclassed proxy overrides methods in the target class, allowing it to add behavior around the original method
 * calls, such as starting or committing a transaction. The primary benefits of CGLIB proxying are:
 * </p>
 * <ul>
 *     <li>Ability to proxy classes without requiring them to implement interfaces.</li>
 *     <li>Compatibility with Spring's {@code @Transactional} and other AOP-based annotations.</li>
 * </ul>
 *
 * <h3>Limitations of CGLIB Proxying:</h3>
 * <p>
 * Since CGLIB creates proxies by subclassing, there are a few limitations:
 * </p>
 * <ul>
 *     <li><b>Final Classes:</b> Classes marked as {@code final} cannot be subclassed, so CGLIB cannot create proxies for them.</li>
 *     <li><b>Final Methods:</b> Methods marked as {@code final} cannot be overridden, so CGLIB proxies cannot add behavior to them.</li>
 * </ul>
 *
 * <h3>Usage in Spring Framework:</h3>
 * <p>
 * By default, Spring will use JDK dynamic proxies for beans that implement one or more interfaces. However, if a bean
 * does not implement any interfaces, Spring will fall back to CGLIB-based proxying to allow AOP functionality
 * (e.g., for transaction management or caching) on the bean.
 * </p>
 *
 * <p>To explicitly enforce CGLIB proxying, you can use the {@code proxyTargetClass = true} setting in
 * {@code @EnableAspectJAutoProxy} or in Spring configuration, as shown below:
 * </p>
 * <pre>
 * &#64;EnableAspectJAutoProxy(proxyTargetClass = true)
 * </pre>
 *
 * <p>Enforcing CGLIB proxying may be necessary when the target class is a concrete class (not an interface) and requires
 * cross-cutting behavior via Spring AOP. However, CGLIB should generally be avoided if JDK dynamic proxies suffice,
 * as CGLIB proxies have a slightly higher memory and performance overhead.
 * </p>
 *
 * <h3>Example:</h3>
 * <pre>
 * &#64;Component
 * public class TransactionalService {
 *
 *     &#64;Transactional
 *     public void performTransaction() {
 *         // Transactional logic here
 *     }
 * }
 * </pre>
 * <p>
 * In the example above, if {@code TransactionalService} does not implement an interface, Spring will use CGLIB to
 * create a subclass-based proxy, enabling transaction management on {@code performTransaction()}.
 * </p>
 *
 * @see org.springframework.aop.framework.ProxyFactoryBean
 * @see org.springframework.transaction.annotation.Transactional
 * @see org.springframework.context.annotation.EnableAspectJAutoProxy
 * @see java.lang.reflect.Proxy
 * @see net.sf.cglib.proxy.Enhancer
 */
public class CGLIB_TransactionalProxy {
}


@Configuration
class CglibProxyConfiguration {

    @Bean
    ApplicationRunner cglibDemo(CglibCustomerService customerService) {
        return args -> customerService.create();
    }

    @Bean
    static CglibBPP cglibBPP() {
        return new CglibBPP();
    }

    static class CglibBPP implements SmartInstantiationAwareBeanPostProcessor {

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof CglibCustomerService) {
                try {
                    return cglib(bean, bean.getClass()).getProxy(bean.getClass().getClassLoader());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            return SmartInstantiationAwareBeanPostProcessor
                    .super.postProcessAfterInitialization(bean, beanName);
        }

        @Override
        public Class<?> determineBeanType(Class<?> beanClass, String beanName) throws BeansException {

            if (beanClass.isAssignableFrom(CglibCustomerService.class))
                return cglib(null, beanClass).getProxyClass(beanClass.getClassLoader());

            return beanClass;
        }
    }

    @Bean
    CglibCustomerService customerService() {
        return new CglibCustomerService();
    }

    private static ProxyFactory cglib(Object target, Class<?> targetClass) {
        var pf = new ProxyFactory();
        pf.setTargetClass(targetClass);
        pf.setInterfaces(targetClass.getInterfaces());
        pf.setProxyTargetClass(true);
        pf.addAdvice((MethodInterceptor) invocation -> {
            var methodName = invocation.getMethod().getName();
            System.out.println("before " + methodName);
            var result = invocation.getMethod().invoke(target, invocation.getArguments());
            System.out.println("after " + methodName);
            return result;
        });
        if (null != target) {
            pf.setTarget(target);
        }
        return pf;
    }

}

//@Configuration
class InterfaceProxyConfiguration {

    static <T> T jdk(T target) throws Exception {
        var pfb = new ProxyFactoryBean();
        pfb.setProxyInterfaces(target.getClass().getInterfaces());
        pfb.setTarget(target);
        pfb.addAdvice((MethodInterceptor) invocation -> {
            try {
                Transactions.handleTxStartFor(invocation.getMethod());
                return invocation.proceed();
            } finally {
                Transactions.handleTxStopFor(invocation.getMethod());
            }
        });
        return (T) pfb.getObject();

    }


    @Bean
    ApplicationRunner interfaceDemo(CustomerService customerService) {
        return args -> {
            customerService.create();
        };
    }

    @Bean
    InterfaceCustomerService interfaceCustomerService() {
        return new InterfaceCustomerService();
    }

    @Bean
    static InterfaceBPP interfaceBPP() {
        return new InterfaceBPP();
    }

    @Bean
    static InterfaceBFIAP interfaceBFIAP() {
        return new InterfaceBFIAP();
    }

    static class InterfaceBFIAP implements BeanFactoryInitializationAotProcessor {
        @Override
        public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
            return (generationContext, beanFactoryInitializationCode) -> generationContext.getRuntimeHints().proxies().registerJdkProxy(
                    CustomerService.class,
                    org.springframework.aop.SpringProxy.class,
                    org.springframework.aop.framework.Advised.class,
                    org.springframework.core.DecoratingProxy.class);
        }

    }

    static class InterfaceBPP implements BeanPostProcessor {

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof InterfaceCustomerService) {
                try {
                    return jdk((CustomerService) bean);
                } //
                catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            return BeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);
        }
    }

}

class Transactions {


    static void handleTxStartFor(Method method) {
        if (method.getAnnotation(MyTransactional.class) != null)
            System.out.println(method.getName() + ": start");
    }

    static void handleTxStopFor(Method method) {
        if (method.getAnnotation(MyTransactional.class) != null)
            System.out.println(method.getName() + ": stop");
    }

}

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@Reflective
@interface MyTransactional {
}


class ForwardingCustomerService
        implements CustomerService {

    private final CustomerService customerService;

    ForwardingCustomerService(CustomerService customerService) {
        this.customerService = customerService;
    }

    @Override
    public void create() {
        System.out.println("----------------------------------------");
        System.out.println("create: start");
        this.customerService.create();
        System.out.println("create: stop");
    }
}


interface CustomerService {

    @MyTransactional
    void create();
}

class InterfaceCustomerService implements CustomerService {

    @Override
    public void create() {
        System.out.println(getClass().getName());
    }
}

class CglibCustomerService {

    @MyTransactional
    public void create() {
        System.out.println(getClass().getName());
    }
}