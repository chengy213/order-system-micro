package com.example.ordersystem.interceptor;//package com.example.ordersystem.interceptor;
//
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//import jakarta.servlet.http.HttpSession;
//import org.springframework.stereotype.Component;
//import org.springframework.web.servlet.HandlerInterceptor;
//
////LoginInterceptor的功能完全被SecurityConfig覆盖了
//@Component
//public class LoginInterceptor implements HandlerInterceptor {
//
//    @Override
//    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        HttpSession session = request.getSession();
//        Object userId = session.getAttribute("userId");
//        if (userId == null) {
//            response.sendRedirect("/login");
//            return false;
//        }
//        return true;
//    }
//}