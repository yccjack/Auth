package com.mystical.cloud.auth.security;

import com.mystical.cloud.auth.config.IgnoreUrlsProperties;
import com.mystical.cloud.auth.security.handler.*;
import com.mystical.cloud.auth.service.SelfUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)
@EnableWebSecurity
public class MySecurityConfig extends WebSecurityConfigurerAdapter {
    @Autowired
    AjaxAuthenticationEntryPoint authenticationEntryPoint;

    @Autowired
    AjaxAuthenticationSuccessHandler authenticationSuccessHandler;

    @Autowired
    AjaxAuthenticationFailureHandler authenticationFailureHandler;

    @Autowired
    AjaxLogoutSuccessHandler logoutSuccessHandler;

    @Autowired
    AjaxAccessDeniedHandler accessDeniedHandler;

    @Autowired
    SelfUserDetailsService userDetailsService;

    @Autowired
    JwtAuthenticationTokenFilter jwtAuthenticationTokenFilter;

    @Autowired
    IgnoreUrlsProperties ignoreUrlsProperties;

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        // ??????????????????????????????
        auth.userDetailsService(userDetailsService).passwordEncoder(new BCryptPasswordEncoder());
    }

    // ????????????token???rest api?????????????????????
    @Override
    public void configure(WebSecurity web) throws Exception {
        ignoreUrlsProperties.getResources().forEach(resource -> web.ignoring().antMatchers(resource));
        web.ignoring(). antMatchers("/swagger-ui.html")
                .antMatchers("/webjars/**")
                .antMatchers("/v2/**")
                .antMatchers("/swagger-resources/**")
                .antMatchers("/auth/*")
                .antMatchers("/signature/*")
                .antMatchers("/**/mq/**");
    }
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        // ?????? CSRF????????????
        http.csrf().disable()
                // ?????? JWT?????????session
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()

                //  ?????????????????? JSON
                .httpBasic().authenticationEntryPoint(authenticationEntryPoint)
                .and()

                // ????????????????????????
                .authorizeRequests()

                // ????????????????????????????????????????????????
                .antMatchers(
                        HttpMethod.GET,
                        "/",
                        "/*.html",
                        "/favicon.ico",
                        "/**/*.html",
                        "/**/*.css",
                        "/**/*.js",
                        "/**/*.jpg",
                        "/**/*.png"
                ).permitAll()
                .anyRequest()
                // ???????????????
                // RBAC ?????? url ??????
                .access("@rbacauthorityservice.hasPermission(request,authentication)")
                .and()
                //????????????
                .formLogin()
                .loginPage("/login")
                // ????????????
                .successHandler(authenticationSuccessHandler)
                // ????????????
                .failureHandler(authenticationFailureHandler)
                .permitAll()
                .and()
                // ??????
                .logout()
                .logoutSuccessHandler(logoutSuccessHandler)
                .permitAll();

        // ?????????
        http.rememberMe().rememberMeParameter("remember-me")
                .userDetailsService(userDetailsService).tokenValiditySeconds(300);
// ???????????? JSON ???????????????
        http.exceptionHandling().accessDeniedHandler(accessDeniedHandler);
        //????????????Filter??????????????????UsernamePasswordAuthenticationFilter????????????json ?????????????????????
        http.addFilterAt(customAuthenticationFilter(),
                UsernamePasswordAuthenticationFilter.class);
// ?????????????????????????????? filter ????????????????????? JWT???
        // JWT Filter
        http.addFilterBefore(jwtAuthenticationTokenFilter, UsernamePasswordAuthenticationFilter.class);
    }

    /**
     * //??????????????????UsernamePasswordAuthenticationFilter
     *
     * @return
     * @throws Exception
     */
    @Bean
    CustomAuthenticationFilter customAuthenticationFilter() throws Exception {
        CustomAuthenticationFilter filter = new CustomAuthenticationFilter();
        filter.setAuthenticationSuccessHandler(authenticationSuccessHandler);
        filter.setAuthenticationFailureHandler(authenticationFailureHandler);
        // ?????????????????????
        filter.setFilterProcessesUrl("/login");

        //????????????????????????WebSecurityConfigurerAdapter?????????AuthenticationManager????????????????????????AuthenticationManager
        filter.setAuthenticationManager(authenticationManagerBean());
        return filter;
    }
}