package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import static com.baomidou.mybatisplus.core.toolkit.Wrappers.query;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result sendcode(String phone, HttpSession session) {

        // 1.判断提交的手机号是否合规


        if (RegexUtils.isPhoneInvalid(phone)) {
            // 1.1 不符合则返回错误信息
            return Result.fail("手机号不符合规范");

        }

        // 1.2 符合生成验证码
        String code = RandomUtil.randomNumbers(6);

        // 2 保存验证码到session
        session.setAttribute("code", code);

        // 3 发送验证码
        log.debug("发送验证码成功，验证码为 {}", code);

        return Result.ok();

    }


    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 实现登录功能
        //验证发送的验证码和手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            Result.fail("手机号格式错误");
        }
        Object code = session.getAttribute("code");
        //如果手机号和验证码不一致，报错
        if (code == null || !code.equals(loginForm.getCode())) {
            return Result.fail("验证码错误");
        }
        //如果一致，根据手机号查询用户
        User user = query().eq("phone", phone).one();

        //查询用户是否存在

        if (user == null) {
            //如果不存在，添加到数据库
            user = saveWithPhone(phone);
        }

        //如果存在，保存用户到session
        session.setAttribute("user", user);

        return Result.ok();
    }

    private User saveWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(6));
        save(user);
        return user;
    }
}
