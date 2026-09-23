package com.znxsgl.config;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;

import java.beans.Introspector;

/**
 * 自定义 mapper Bean 名生成器。
 *
 * 背景：小林（com.coursenote.mapper）与 aiStudy（com.znxsgl.mapper）存在同名 Mapper 接口
 * （如 UserMapper、CourseMapper），默认 Bean 名均为简单类名去首字母小写（如 "userMapper"），
 * 会在 Spring 容器中冲突。此生成器仅对 com.znxsgl 包的 Mapper 追加 "znxsgl" 前缀
 * （如 znxsglUserMapper），com.coursenote 仍保持原生 Bean 名，从而两套 Mapper 共存，
 * 且不影响按类型注入。
 */
public class DistinctBeanNameGenerator implements BeanNameGenerator {

    @Override
    public String generateBeanName(BeanDefinition definition, BeanDefinitionRegistry registry) {
        String beanClassName = definition.getBeanClassName();
        if (beanClassName == null) {
            return "znxsglMapper";
        }
        int idx = beanClassName.lastIndexOf('.');
        String simpleName = (idx >= 0 ? beanClassName.substring(idx + 1) : beanClassName);
        if (beanClassName.startsWith("com.znxsgl.")) {
            // znxsgl 包 Mapper 统一加前缀，如 UserMapper -> znxsglUserMapper
            return "znxsgl" + simpleName;
        }
        // 其余包（com.coursenote）保持默认规则：简单类名首字母小写
        return Introspector.decapitalize(simpleName);
    }
}