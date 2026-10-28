# SmartFix 系统运行场景与全部 Java 文件关联索引

基于当前本地源码。用于配合聊天中的场景说明逐个查文件，不修改业务代码。

## 如何阅读

本索引逐一覆盖 src/main/java 下的 Java 文件。每项包含参与场景、职责、直接使用的项目类型以及被其他项目类型引用的关系。

“类型引用”来自源码的显式类型使用，不等于运行期方法调用链。Spring 组件扫描、HTTP 路由、事务代理、事件订阅和接口动态实现可能没有直接的调用者类型引用。JDK 与第三方框架类型未逐项列出。

正文场景编号与聊天讲解一致。annotation 和 audit 的内部细节已学过，本索引仍保留它们，避免跨模块入口遗漏。

共 267 个主 Java 文件。

## 入口 模块

### [SmartFixApplication.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/SmartFixApplication.java)

- 位置：`src/main/java/com/smartfix/SmartFixApplication.java`
- 运行场景：场景01应用启动。
- 职责：应用入口，启动 Spring Boot；根包组件扫描从这里展开。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

## auth 模块

### [SecurityConfig.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/config/SecurityConfig.java)

- 位置：`src/main/java/com/smartfix/auth/config/SecurityConfig.java`
- 运行场景：场景02注册、03登录、04每次访问与修改密码。
- 职责：声明认证提供者、登录注销、安全过滤器顺序和 URL 角色规则。
- 使用的项目类型：[ActiveAccountFilter.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/ActiveAccountFilter.java)、[SelectedAccountType.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SelectedAccountType.java)、[SelectedAccountTypeAuthenticationProvider.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SelectedAccountTypeAuthenticationProvider.java)、[SelectedAccountTypeFailureHandler.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SelectedAccountTypeFailureHandler.java)、[SmartFixUserDetailsService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/service/SmartFixUserDetailsService.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [CsrfTokens.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/controller/CsrfTokens.java)

- 位置：`src/main/java/com/smartfix/auth/controller/CsrfTokens.java`
- 运行场景：场景02注册、03登录、04每次访问与修改密码。
- 职责：在登录注册页面需要时解析 CSRF token。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[LoginController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/controller/LoginController.java)、[RegistrationController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/controller/RegistrationController.java)

### [LoginController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/controller/LoginController.java)

- 位置：`src/main/java/com/smartfix/auth/controller/LoginController.java`
- 运行场景：场景02注册、03登录、04每次访问与修改密码。
- 职责：提供登录页及页面数据；密码认证由安全过滤链和 Provider 执行。
- 使用的项目类型：[CsrfTokens.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/controller/CsrfTokens.java)、[SelectedAccountType.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SelectedAccountType.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [PasswordController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/controller/PasswordController.java)

- 位置：`src/main/java/com/smartfix/auth/controller/PasswordController.java`
- 运行场景：场景02注册、03登录、04每次访问与修改密码。
- 职责：接收所属场景的 HTTP 请求，调用服务并选择页面、跳转或数据响应。
- 使用的项目类型：[SmartFixUserDetails.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SmartFixUserDetails.java)、[InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [RegistrationController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/controller/RegistrationController.java)

- 位置：`src/main/java/com/smartfix/auth/controller/RegistrationController.java`
- 运行场景：场景02注册、03登录、04每次访问与修改密码。
- 职责：接收所属场景的 HTTP 请求，调用服务并选择页面、跳转或数据响应。
- 使用的项目类型：[CsrfTokens.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/controller/CsrfTokens.java)、[RegistrationRateLimiter.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/service/RegistrationRateLimiter.java)、[BusinessConflictException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/BusinessConflictException.java)、[InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)、[RegistrationCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/RegistrationCommand.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)、[PasswordPolicy.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/validation/PasswordPolicy.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [ActiveAccountFilter.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/ActiveAccountFilter.java)

- 位置：`src/main/java/com/smartfix/auth/security/ActiveAccountFilter.java`
- 运行场景：场景02注册、03登录、04每次访问与修改密码。
- 职责：复查账号状态、安全版本和强制改密，决定继续请求还是失效退出。
- 使用的项目类型：[SmartFixUserDetails.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SmartFixUserDetails.java)、[ResourceNotFoundException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/ResourceNotFoundException.java)、[AccountStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/AccountStatus.java)、[UserAccessResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/UserAccessResponse.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)
- 引用它的项目文件：[SecurityConfig.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/config/SecurityConfig.java)

### [SelectedAccountType.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SelectedAccountType.java)

- 位置：`src/main/java/com/smartfix/auth/security/SelectedAccountType.java`
- 运行场景：场景02注册、03登录、04每次访问与修改密码。
- 职责：承载登录表单选择的账号类型。
- 使用的项目类型：[Role.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/Role.java)
- 引用它的项目文件：[SecurityConfig.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/config/SecurityConfig.java)、[LoginController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/controller/LoginController.java)、[SelectedAccountTypeAuthenticationProvider.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SelectedAccountTypeAuthenticationProvider.java)、[SelectedAccountTypeFailureHandler.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SelectedAccountTypeFailureHandler.java)

### [SelectedAccountTypeAuthenticationProvider.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SelectedAccountTypeAuthenticationProvider.java)

- 位置：`src/main/java/com/smartfix/auth/security/SelectedAccountTypeAuthenticationProvider.java`
- 运行场景：场景02注册、03登录、04每次访问与修改密码。
- 职责：在账号密码认证基础上核对选择类型与实际角色。
- 使用的项目类型：[SelectedAccountType.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SelectedAccountType.java)、[SelectedAccountTypeMismatchException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SelectedAccountTypeMismatchException.java)、[SmartFixUserDetails.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SmartFixUserDetails.java)
- 引用它的项目文件：[SecurityConfig.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/config/SecurityConfig.java)

### [SelectedAccountTypeFailureHandler.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SelectedAccountTypeFailureHandler.java)

- 位置：`src/main/java/com/smartfix/auth/security/SelectedAccountTypeFailureHandler.java`
- 运行场景：场景02注册、03登录、04每次访问与修改密码。
- 职责：把类型不匹配等登录失败转换为登录页反馈。
- 使用的项目类型：[SelectedAccountType.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SelectedAccountType.java)、[SelectedAccountTypeMismatchException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SelectedAccountTypeMismatchException.java)
- 引用它的项目文件：[SecurityConfig.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/config/SecurityConfig.java)

### [SelectedAccountTypeMismatchException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SelectedAccountTypeMismatchException.java)

- 位置：`src/main/java/com/smartfix/auth/security/SelectedAccountTypeMismatchException.java`
- 运行场景：场景02注册、03登录、04每次访问与修改密码。
- 职责：表达所选账号类型与实际身份不一致的认证失败。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[SelectedAccountTypeAuthenticationProvider.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SelectedAccountTypeAuthenticationProvider.java)、[SelectedAccountTypeFailureHandler.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SelectedAccountTypeFailureHandler.java)

### [SmartFixUserDetails.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SmartFixUserDetails.java)

- 位置：`src/main/java/com/smartfix/auth/security/SmartFixUserDetails.java`
- 运行场景：场景02注册、03登录、04每次访问与修改密码。
- 职责：保存 Session 使用的用户 ID、角色、安全版本及认证信息。
- 使用的项目类型：[AccountStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/AccountStatus.java)、[Role.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/Role.java)、[UserAuthenticationData.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/UserAuthenticationData.java)
- 引用它的项目文件：[AnnouncementController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/controller/AnnouncementController.java)、[AuditController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/audit/controller/AuditController.java)、[PasswordController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/controller/PasswordController.java)、[ActiveAccountFilter.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/ActiveAccountFilter.java)、[SelectedAccountTypeAuthenticationProvider.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SelectedAccountTypeAuthenticationProvider.java)、[SmartFixUserDetailsService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/service/SmartFixUserDetailsService.java)、[HomeController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/web/HomeController.java)、[NavigationAdvice.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/web/NavigationAdvice.java)、[NotificationNavigationAdvice.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/web/NotificationNavigationAdvice.java)、[AdminCommunityController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/AdminCommunityController.java)、[CommunityAnswerController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityAnswerController.java)、[CommunityQuestionController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityQuestionController.java)、[CommunityReportController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityReportController.java)、[DispatchController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/controller/DispatchController.java)、[DeliveryAdministrationController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/controller/DeliveryAdministrationController.java)、[NotificationController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/controller/NotificationController.java)、[AdminWorkspaceController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/controller/AdminWorkspaceController.java)、[CampusMapController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/controller/CampusMapController.java)、[DashboardController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/controller/DashboardController.java)、[AttachmentController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/AttachmentController.java)、[AttachmentInspectionController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/AttachmentInspectionController.java)、[RequestConfirmationController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestConfirmationController.java)、[RequestQueryController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestQueryController.java)、[RequestReviewController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestReviewController.java)、[RequestSubmissionController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestSubmissionController.java)、[SlaController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/controller/SlaController.java)、[AdminTechnicianController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/controller/AdminTechnicianController.java)、[TechnicianProfileController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/controller/TechnicianProfileController.java)、[WorkOrderController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/controller/WorkOrderController.java)

### [RegistrationRateLimiter.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/service/RegistrationRateLimiter.java)

- 位置：`src/main/java/com/smartfix/auth/service/RegistrationRateLimiter.java`
- 运行场景：场景02注册、03登录、04每次访问与修改密码。
- 职责：限制公开注册频率；属于进程内状态。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[RegistrationController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/controller/RegistrationController.java)

### [SmartFixUserDetailsService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/service/SmartFixUserDetailsService.java)

- 位置：`src/main/java/com/smartfix/auth/service/SmartFixUserDetailsService.java`
- 运行场景：场景02注册、03登录、04每次访问与修改密码。
- 职责：把用户名交给用户模块查询认证数据，再转换为 Spring Security 身份。
- 使用的项目类型：[SmartFixUserDetails.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SmartFixUserDetails.java)、[ResourceNotFoundException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/ResourceNotFoundException.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)
- 引用它的项目文件：[SecurityConfig.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/config/SecurityConfig.java)

## user 模块

### [BootstrapAdminInitializer.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/config/BootstrapAdminInitializer.java)

- 位置：`src/main/java/com/smartfix/user/config/BootstrapAdminInitializer.java`
- 运行场景：场景01初始化、02注册、03认证、05管理员账号操作。
- 职责：应用上下文准备后调用初始化服务。
- 使用的项目类型：[UserBootstrapService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserBootstrapService.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [BootstrapAdminProperties.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/config/BootstrapAdminProperties.java)

- 位置：`src/main/java/com/smartfix/user/config/BootstrapAdminProperties.java`
- 运行场景：场景01初始化、02注册、03认证、05管理员账号操作。
- 职责：读取一次性管理员初始化配置。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[UserBootstrapService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserBootstrapService.java)

### [PasswordConfig.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/config/PasswordConfig.java)

- 位置：`src/main/java/com/smartfix/user/config/PasswordConfig.java`
- 运行场景：场景01初始化、02注册、03认证、05管理员账号操作。
- 职责：提供密码编码器供创建账号和登录验证共同使用。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [UserManagementController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/controller/UserManagementController.java)

- 位置：`src/main/java/com/smartfix/user/controller/UserManagementController.java`
- 运行场景：场景01初始化、02注册、03认证、05管理员账号操作。
- 职责：接收所属场景的 HTTP 请求，调用服务并选择页面、跳转或数据响应。
- 使用的项目类型：[BusinessConflictException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/BusinessConflictException.java)、[InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)、[ResourceNotFoundException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/ResourceNotFoundException.java)、[AccountStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/AccountStatus.java)、[Role.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/Role.java)、[ChangeAccountStatusCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/ChangeAccountStatusCommand.java)、[ChangeUserRoleCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/ChangeUserRoleCommand.java)、[CreateUserCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/CreateUserCommand.java)、[AccountAdministrationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/AccountAdministrationService.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [AccountStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/AccountStatus.java)

- 位置：`src/main/java/com/smartfix/user/domain/AccountStatus.java`
- 运行场景：场景01初始化、02注册、03认证、05管理员账号操作。
- 职责：限制该业务概念的合法取值，供规则判断、持久化或页面选项复用。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[AuditService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/audit/service/AuditService.java)、[ActiveAccountFilter.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/ActiveAccountFilter.java)、[SmartFixUserDetails.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SmartFixUserDetails.java)、[CommunityAccessGuard.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityAccessGuard.java)、[AssignmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/AssignmentService.java)、[DispatchPageService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/DispatchPageService.java)、[CampusMapService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/CampusMapService.java)、[RequestAccessService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestAccessService.java)、[RequestAssignmentAccessService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestAssignmentAccessService.java)、[RequestLifecycleService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestLifecycleService.java)、[RequestReviewService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestReviewService.java)、[RequestSubmissionService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestSubmissionService.java)、[TechnicianDirectoryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/service/TechnicianDirectoryService.java)、[UserManagementController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/controller/UserManagementController.java)、[User.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/User.java)、[ChangeAccountStatusCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/ChangeAccountStatusCommand.java)、[UserAccessResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/UserAccessResponse.java)、[UserAuthenticationData.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/UserAuthenticationData.java)、[UserSummaryResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/UserSummaryResponse.java)、[UserRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/repository/UserRepository.java)、[AccountAdministrationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/AccountAdministrationService.java)、[NotificationContactService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/NotificationContactService.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)、[WorkOrderService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderService.java)

### [Role.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/Role.java)

- 位置：`src/main/java/com/smartfix/user/domain/Role.java`
- 运行场景：场景01初始化、02注册、03认证、05管理员账号操作。
- 职责：限制该业务概念的合法取值，供规则判断、持久化或页面选项复用。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[AuditService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/audit/service/AuditService.java)、[SelectedAccountType.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SelectedAccountType.java)、[SmartFixUserDetails.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SmartFixUserDetails.java)、[HomeController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/web/HomeController.java)、[CommunityAccessGuard.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityAccessGuard.java)、[AssignmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/AssignmentService.java)、[DispatchPageService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/DispatchPageService.java)、[CampusMapService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/CampusMapService.java)、[RequestTransition.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestTransition.java)、[RequestAccessService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestAccessService.java)、[RequestAssignmentAccessService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestAssignmentAccessService.java)、[RequestConfirmationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestConfirmationService.java)、[RequestLifecycleService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestLifecycleService.java)、[RequestPresentationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestPresentationService.java)、[RequestQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestQueryService.java)、[RequestReviewService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestReviewService.java)、[RequestSubmissionService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestSubmissionService.java)、[AdminTechnicianController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/controller/AdminTechnicianController.java)、[TechnicianDirectoryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/service/TechnicianDirectoryService.java)、[UserManagementController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/controller/UserManagementController.java)、[User.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/User.java)、[ChangeUserRoleCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/ChangeUserRoleCommand.java)、[CreateUserCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/CreateUserCommand.java)、[UserAccessResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/UserAccessResponse.java)、[UserAuthenticationData.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/UserAuthenticationData.java)、[UserSummaryResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/UserSummaryResponse.java)、[UserRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/repository/UserRepository.java)、[AccountAdministrationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/AccountAdministrationService.java)、[NotificationContactService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/NotificationContactService.java)、[UserBootstrapService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserBootstrapService.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)、[WorkOrderService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderService.java)

### [User.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/User.java)

- 位置：`src/main/java/com/smartfix/user/domain/User.java`
- 运行场景：场景01初始化、02注册、03认证、05管理员账号操作。
- 职责：保存所属业务对象的状态和规则，由服务创建或修改，并由仓库持久化。
- 使用的项目类型：[AccountStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/AccountStatus.java)、[Role.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/Role.java)
- 引用它的项目文件：[CreateUserCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/CreateUserCommand.java)、[RegistrationCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/RegistrationCommand.java)、[UserRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/repository/UserRepository.java)、[NotificationContactService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/NotificationContactService.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)

### [ChangeAccountStatusCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/ChangeAccountStatusCommand.java)

- 位置：`src/main/java/com/smartfix/user/dto/ChangeAccountStatusCommand.java`
- 运行场景：场景01初始化、02注册、03认证、05管理员账号操作。
- 职责：承载该操作的输入数据或表单值，交给 Controller 和 Service 校验、执行。
- 使用的项目类型：[AccountStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/AccountStatus.java)
- 引用它的项目文件：[UserManagementController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/controller/UserManagementController.java)、[AccountAdministrationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/AccountAdministrationService.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)

### [ChangeUserRoleCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/ChangeUserRoleCommand.java)

- 位置：`src/main/java/com/smartfix/user/dto/ChangeUserRoleCommand.java`
- 运行场景：场景01初始化、02注册、03认证、05管理员账号操作。
- 职责：承载该操作的输入数据或表单值，交给 Controller 和 Service 校验、执行。
- 使用的项目类型：[Role.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/Role.java)
- 引用它的项目文件：[UserManagementController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/controller/UserManagementController.java)、[AccountAdministrationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/AccountAdministrationService.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)

### [CreateUserCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/CreateUserCommand.java)

- 位置：`src/main/java/com/smartfix/user/dto/CreateUserCommand.java`
- 运行场景：场景01初始化、02注册、03认证、05管理员账号操作。
- 职责：承载该操作的输入数据或表单值，交给 Controller 和 Service 校验、执行。
- 使用的项目类型：[Role.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/Role.java)、[User.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/User.java)、[ValidPassword.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/validation/ValidPassword.java)
- 引用它的项目文件：[UserManagementController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/controller/UserManagementController.java)、[AccountAdministrationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/AccountAdministrationService.java)、[UserBootstrapService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserBootstrapService.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)

### [RegistrationCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/RegistrationCommand.java)

- 位置：`src/main/java/com/smartfix/user/dto/RegistrationCommand.java`
- 运行场景：场景01初始化、02注册、03认证、05管理员账号操作。
- 职责：承载该操作的输入数据或表单值，交给 Controller 和 Service 校验、执行。
- 使用的项目类型：[User.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/User.java)、[ValidPassword.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/validation/ValidPassword.java)
- 引用它的项目文件：[RegistrationController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/controller/RegistrationController.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)

### [UserAccessResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/UserAccessResponse.java)

- 位置：`src/main/java/com/smartfix/user/dto/UserAccessResponse.java`
- 运行场景：场景01初始化、02注册、03认证、05管理员账号操作。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：[AccountStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/AccountStatus.java)、[Role.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/Role.java)
- 引用它的项目文件：[ActiveAccountFilter.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/ActiveAccountFilter.java)、[CommunityAccessGuard.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityAccessGuard.java)、[RequestAccessService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestAccessService.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)

### [UserAuthenticationData.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/UserAuthenticationData.java)

- 位置：`src/main/java/com/smartfix/user/dto/UserAuthenticationData.java`
- 运行场景：场景01初始化、02注册、03认证、05管理员账号操作。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：[AccountStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/AccountStatus.java)、[Role.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/Role.java)
- 引用它的项目文件：[SmartFixUserDetails.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SmartFixUserDetails.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)

### [UserSummaryResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/UserSummaryResponse.java)

- 位置：`src/main/java/com/smartfix/user/dto/UserSummaryResponse.java`
- 运行场景：场景01初始化、02注册、03认证、05管理员账号操作。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：[AccountStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/AccountStatus.java)、[Role.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/Role.java)
- 引用它的项目文件：[TechnicianDirectoryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/service/TechnicianDirectoryService.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)

### [NotificationContactChangedEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/event/NotificationContactChangedEvent.java)

- 位置：`src/main/java/com/smartfix/user/event/NotificationContactChangedEvent.java`
- 运行场景：场景01初始化、02注册、03认证、05管理员账号操作。
- 职责：承载已经发生的业务事实；事件发布者与监听者通过该类型协作。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[NotificationContactAuditListener.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/audit/listener/NotificationContactAuditListener.java)、[NotificationContactService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/NotificationContactService.java)

### [UserRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/repository/UserRepository.java)

- 位置：`src/main/java/com/smartfix/user/repository/UserRepository.java`
- 运行场景：场景01初始化、02注册、03认证、05管理员账号操作。
- 职责：为所属业务对象提供数据库查询与持久化；通常由本模块服务调用。
- 使用的项目类型：[AccountStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/AccountStatus.java)、[Role.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/Role.java)、[User.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/User.java)
- 引用它的项目文件：[NotificationContactService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/NotificationContactService.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)

### [AccountAdministrationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/AccountAdministrationService.java)

- 位置：`src/main/java/com/smartfix/user/service/AccountAdministrationService.java`
- 运行场景：场景01初始化、02注册、03认证、05管理员账号操作。
- 职责：管理员账号操作的业务边界，联合用户变更和审计。
- 使用的项目类型：[AuditService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/audit/service/AuditService.java)、[AccountStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/AccountStatus.java)、[Role.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/Role.java)、[ChangeAccountStatusCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/ChangeAccountStatusCommand.java)、[ChangeUserRoleCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/ChangeUserRoleCommand.java)、[CreateUserCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/CreateUserCommand.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)
- 引用它的项目文件：[UserManagementController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/controller/UserManagementController.java)

### [NotificationContactService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/NotificationContactService.java)

- 位置：`src/main/java/com/smartfix/user/service/NotificationContactService.java`
- 运行场景：场景01初始化、02注册、03认证、05管理员账号操作。
- 职责：维护既有账号的通知地址并发布地址变更事件。
- 使用的项目类型：[InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)、[ResourceNotFoundException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/ResourceNotFoundException.java)、[AccountStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/AccountStatus.java)、[Role.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/Role.java)、[User.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/User.java)、[NotificationContactChangedEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/event/NotificationContactChangedEvent.java)、[UserRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/repository/UserRepository.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)
- 引用它的项目文件：[DeliveryAdministrationController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/controller/DeliveryAdministrationController.java)、[SlaNotificationListener.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/listener/SlaNotificationListener.java)、[DeliveryAdministrationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/DeliveryAdministrationService.java)、[EmailDeliveryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/EmailDeliveryService.java)、[AttachmentInspectionService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/AttachmentInspectionService.java)、[SlaPolicyService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/service/SlaPolicyService.java)

### [UserBootstrapService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserBootstrapService.java)

- 位置：`src/main/java/com/smartfix/user/service/UserBootstrapService.java`
- 运行场景：场景01初始化、02注册、03认证、05管理员账号操作。
- 职责：执行受控管理员初始化并维护数据库初始化标记。
- 使用的项目类型：[BootstrapAdminProperties.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/config/BootstrapAdminProperties.java)、[Role.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/Role.java)、[CreateUserCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/CreateUserCommand.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)
- 引用它的项目文件：[BootstrapAdminInitializer.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/config/BootstrapAdminInitializer.java)

### [UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)

- 位置：`src/main/java/com/smartfix/user/service/UserService.java`
- 运行场景：场景01初始化、02注册、03认证、05管理员账号操作。
- 职责：参与所属场景的协作类型，具体依赖见下方文件关联。
- 使用的项目类型：[BusinessConflictException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/BusinessConflictException.java)、[InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)、[ResourceNotFoundException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/ResourceNotFoundException.java)、[AccountStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/AccountStatus.java)、[Role.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/Role.java)、[User.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/User.java)、[ChangeAccountStatusCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/ChangeAccountStatusCommand.java)、[ChangeUserRoleCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/ChangeUserRoleCommand.java)、[CreateUserCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/CreateUserCommand.java)、[RegistrationCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/RegistrationCommand.java)、[UserAccessResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/UserAccessResponse.java)、[UserAuthenticationData.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/UserAuthenticationData.java)、[UserSummaryResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/UserSummaryResponse.java)、[UserRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/repository/UserRepository.java)、[PasswordPolicy.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/validation/PasswordPolicy.java)
- 引用它的项目文件：[AuditService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/audit/service/AuditService.java)、[SecurityConfig.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/config/SecurityConfig.java)、[PasswordController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/controller/PasswordController.java)、[RegistrationController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/controller/RegistrationController.java)、[ActiveAccountFilter.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/ActiveAccountFilter.java)、[SmartFixUserDetailsService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/service/SmartFixUserDetailsService.java)、[CommunityAccessGuard.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityAccessGuard.java)、[CommunityQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityQueryService.java)、[AssignmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/AssignmentService.java)、[DispatchPageService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/DispatchPageService.java)、[CampusMapService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/CampusMapService.java)、[RequestAccessService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestAccessService.java)、[RequestAssignmentAccessService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestAssignmentAccessService.java)、[RequestConfirmationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestConfirmationService.java)、[RequestLifecycleService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestLifecycleService.java)、[RequestPresentationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestPresentationService.java)、[RequestReviewService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestReviewService.java)、[RequestSubmissionService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestSubmissionService.java)、[AdminTechnicianController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/controller/AdminTechnicianController.java)、[TechnicianDirectoryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/service/TechnicianDirectoryService.java)、[UserManagementController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/controller/UserManagementController.java)、[AccountAdministrationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/AccountAdministrationService.java)、[NotificationContactService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/NotificationContactService.java)、[UserBootstrapService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserBootstrapService.java)、[WorkOrderService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderService.java)

### [PasswordConstraintValidator.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/validation/PasswordConstraintValidator.java)

- 位置：`src/main/java/com/smartfix/user/validation/PasswordConstraintValidator.java`
- 运行场景：场景01初始化、02注册、03认证、05管理员账号操作。
- 职责：执行 ValidPassword 声明的校验规则。
- 使用的项目类型：[PasswordPolicy.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/validation/PasswordPolicy.java)、[ValidPassword.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/validation/ValidPassword.java)
- 引用它的项目文件：[ValidPassword.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/validation/ValidPassword.java)

### [PasswordPolicy.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/validation/PasswordPolicy.java)

- 位置：`src/main/java/com/smartfix/user/validation/PasswordPolicy.java`
- 运行场景：场景01初始化、02注册、03认证、05管理员账号操作。
- 职责：集中定义密码要求。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[RegistrationController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/controller/RegistrationController.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)、[PasswordConstraintValidator.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/validation/PasswordConstraintValidator.java)

### [ValidPassword.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/validation/ValidPassword.java)

- 位置：`src/main/java/com/smartfix/user/validation/ValidPassword.java`
- 运行场景：场景01初始化、02注册、03认证、05管理员账号操作。
- 职责：声明项目自定义密码校验注解。
- 使用的项目类型：[PasswordConstraintValidator.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/validation/PasswordConstraintValidator.java)
- 引用它的项目文件：[CreateUserCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/CreateUserCommand.java)、[RegistrationCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/RegistrationCommand.java)、[PasswordConstraintValidator.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/validation/PasswordConstraintValidator.java)

## common 模块

### [LegacyMigrationCompatibility.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/configuration/LegacyMigrationCompatibility.java)

- 位置：`src/main/java/com/smartfix/common/configuration/LegacyMigrationCompatibility.java`
- 运行场景：场景01启动、04通用访问、06首页、22统一异常。
- 职责：处理特定历史迁移兼容问题，不是常规业务入口。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [LegacyMigrationRecoveryConfiguration.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/configuration/LegacyMigrationRecoveryConfiguration.java)

- 位置：`src/main/java/com/smartfix/common/configuration/LegacyMigrationRecoveryConfiguration.java`
- 运行场景：场景01启动、04通用访问、06首页、22统一异常。
- 职责：将受控历史数据库恢复配置接入启动流程。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [TimeConfig.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/configuration/TimeConfig.java)

- 位置：`src/main/java/com/smartfix/common/configuration/TimeConfig.java`
- 运行场景：场景01启动、04通用访问、06首页、22统一异常。
- 职责：提供可注入时钟及时间相关配置。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [BusinessConflictException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/BusinessConflictException.java)

- 位置：`src/main/java/com/smartfix/common/exception/BusinessConflictException.java`
- 运行场景：场景01启动、04通用访问、06首页、22统一异常。
- 职责：参与所属场景的协作类型，具体依赖见下方文件关联。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[Announcement.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/domain/Announcement.java)、[RegistrationController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/controller/RegistrationController.java)、[GlobalExceptionHandler.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/GlobalExceptionHandler.java)、[AdminCommunityController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/AdminCommunityController.java)、[CommunityAnswerController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityAnswerController.java)、[CommunityReportController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityReportController.java)、[CommunityAnswer.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityAnswer.java)、[CommunityQuestion.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityQuestion.java)、[CommunityAnswerService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityAnswerService.java)、[CommunityModerationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityModerationService.java)、[CommunityQuestionService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityQuestionService.java)、[DispatchController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/controller/DispatchController.java)、[AssignmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/AssignmentService.java)、[DeliveryAdministrationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/DeliveryAdministrationService.java)、[MaintenanceRequest.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceRequest.java)、[RequestAssignmentAccessService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestAssignmentAccessService.java)、[RequestConfirmationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestConfirmationService.java)、[RequestLifecycleService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestLifecycleService.java)、[TechnicianProfileController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/controller/TechnicianProfileController.java)、[TechnicianDirectoryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/service/TechnicianDirectoryService.java)、[TechnicianWorkloadService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/service/TechnicianWorkloadService.java)、[UserManagementController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/controller/UserManagementController.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)、[WorkOrderController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/controller/WorkOrderController.java)、[WorkOrder.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/domain/WorkOrder.java)、[WorkOrderLifecycleParticipant.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderLifecycleParticipant.java)、[WorkOrderService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderService.java)

### [GlobalExceptionHandler.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/GlobalExceptionHandler.java)

- 位置：`src/main/java/com/smartfix/common/exception/GlobalExceptionHandler.java`
- 运行场景：场景01启动、04通用访问、06首页、22统一异常。
- 职责：统一映射业务、校验、权限、并发等异常。
- 使用的项目类型：[BusinessConflictException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/BusinessConflictException.java)、[InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)、[ResourceNotFoundException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/ResourceNotFoundException.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)

- 位置：`src/main/java/com/smartfix/common/exception/InputValidationException.java`
- 运行场景：场景01启动、04通用访问、06首页、22统一异常。
- 职责：参与所属场景的协作类型，具体依赖见下方文件关联。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[Announcement.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/domain/Announcement.java)、[AuditService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/audit/service/AuditService.java)、[PasswordController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/controller/PasswordController.java)、[RegistrationController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/controller/RegistrationController.java)、[GlobalExceptionHandler.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/GlobalExceptionHandler.java)、[AdminCommunityController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/AdminCommunityController.java)、[CommunityAnswerController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityAnswerController.java)、[CommunityQuestionController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityQuestionController.java)、[CommunityAnswerService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityAnswerService.java)、[CommunityModerationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityModerationService.java)、[CommunityQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityQueryService.java)、[CommunityQuestionService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityQuestionService.java)、[DispatchController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/controller/DispatchController.java)、[AssignmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/AssignmentService.java)、[FacilityController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/controller/FacilityController.java)、[CampusMapService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/CampusMapService.java)、[FacilityService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/FacilityService.java)、[LocationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/LocationService.java)、[ReportController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/controller/ReportController.java)、[OperationalReportService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/service/OperationalReportService.java)、[RequestReviewController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestReviewController.java)、[RequestSubmissionController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestSubmissionController.java)、[RequestFeedback.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestFeedback.java)、[RequestLifecycleService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestLifecycleService.java)、[RequestQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestQueryService.java)、[RequestReadService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestReadService.java)、[RequestReviewService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestReviewService.java)、[RequestSubmissionService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestSubmissionService.java)、[SlaPolicyService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/service/SlaPolicyService.java)、[TechnicianProfileController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/controller/TechnicianProfileController.java)、[TechnicianDirectoryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/service/TechnicianDirectoryService.java)、[TechnicianWorkloadService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/service/TechnicianWorkloadService.java)、[UserManagementController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/controller/UserManagementController.java)、[NotificationContactService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/NotificationContactService.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)、[WorkOrderController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/controller/WorkOrderController.java)、[RepairRecord.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/domain/RepairRecord.java)、[WorkOrder.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/domain/WorkOrder.java)、[WorkOrderService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderService.java)

### [ResourceNotFoundException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/ResourceNotFoundException.java)

- 位置：`src/main/java/com/smartfix/common/exception/ResourceNotFoundException.java`
- 运行场景：场景01启动、04通用访问、06首页、22统一异常。
- 职责：参与所属场景的协作类型，具体依赖见下方文件关联。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[AnnouncementService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/service/AnnouncementService.java)、[AuditService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/audit/service/AuditService.java)、[ActiveAccountFilter.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/ActiveAccountFilter.java)、[SmartFixUserDetailsService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/service/SmartFixUserDetailsService.java)、[GlobalExceptionHandler.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/GlobalExceptionHandler.java)、[CommunityQuestionController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityQuestionController.java)、[CommunityAccessGuard.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityAccessGuard.java)、[CommunityAnswerService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityAnswerService.java)、[CommunityModerationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityModerationService.java)、[CommunityQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityQueryService.java)、[CommunityQuestionService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityQuestionService.java)、[CampusMapService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/CampusMapService.java)、[FacilityService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/FacilityService.java)、[LocationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/LocationService.java)、[DeliveryAdministrationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/DeliveryAdministrationService.java)、[NotificationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/NotificationService.java)、[AttachmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/AttachmentService.java)、[RequestAccessService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestAccessService.java)、[RequestConfirmationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestConfirmationService.java)、[RequestEvidenceService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestEvidenceService.java)、[RequestLifecycleService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestLifecycleService.java)、[RequestReadService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestReadService.java)、[RequestReviewService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestReviewService.java)、[RequestSubmissionService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestSubmissionService.java)、[TechnicianDirectoryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/service/TechnicianDirectoryService.java)、[UserManagementController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/controller/UserManagementController.java)、[NotificationContactService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/NotificationContactService.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)、[WorkOrderService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderService.java)

### [HomeController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/web/HomeController.java)

- 位置：`src/main/java/com/smartfix/common/web/HomeController.java`
- 运行场景：场景01启动、04通用访问、06首页、22统一异常。
- 职责：按角色分流；为请求者首页读取最近报修和可见公告。
- 使用的项目类型：[AnnouncementService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/service/AnnouncementService.java)、[SmartFixUserDetails.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SmartFixUserDetails.java)、[RequestAssignmentAccessService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestAssignmentAccessService.java)、[RequestQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestQueryService.java)、[Role.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/Role.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [NavigationAdvice.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/web/NavigationAdvice.java)

- 位置：`src/main/java/com/smartfix/common/web/NavigationAdvice.java`
- 运行场景：场景01启动、04通用访问、06首页、22统一异常。
- 职责：为页面补充统一导航和身份信息。
- 使用的项目类型：[SmartFixUserDetails.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SmartFixUserDetails.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [NotificationNavigationAdvice.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/web/NotificationNavigationAdvice.java)

- 位置：`src/main/java/com/smartfix/common/web/NotificationNavigationAdvice.java`
- 运行场景：场景01启动、04通用访问、06首页、22统一异常。
- 职责：为页面补充当前接收者的 navUnreadCount。
- 使用的项目类型：[SmartFixUserDetails.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SmartFixUserDetails.java)、[NotificationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/NotificationService.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

## request 模块

### [AttachmentProperties.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/config/AttachmentProperties.java)

- 位置：`src/main/java/com/smartfix/request/config/AttachmentProperties.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：读取或声明所属功能的配置，供启动与运行期组件使用。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[RequestSubmissionController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestSubmissionController.java)、[AttachmentInspectionService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/AttachmentInspectionService.java)、[LocalAttachmentStorageService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/storage/LocalAttachmentStorageService.java)、[AttachmentValidator.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/validation/AttachmentValidator.java)

### [RequestWorkflowProperties.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/config/RequestWorkflowProperties.java)

- 位置：`src/main/java/com/smartfix/request/config/RequestWorkflowProperties.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：读取或声明所属功能的配置，供启动与运行期组件使用。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[RequestLifecycleService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestLifecycleService.java)

### [AttachmentController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/AttachmentController.java)

- 位置：`src/main/java/com/smartfix/request/controller/AttachmentController.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：接收所属场景的 HTTP 请求，调用服务并选择页面、跳转或数据响应。
- 使用的项目类型：[SmartFixUserDetails.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SmartFixUserDetails.java)、[ReadableAttachment.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/ReadableAttachment.java)、[AttachmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/AttachmentService.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [AttachmentInspectionController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/AttachmentInspectionController.java)

- 位置：`src/main/java/com/smartfix/request/controller/AttachmentInspectionController.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：接收所属场景的 HTTP 请求，调用服务并选择页面、跳转或数据响应。
- 使用的项目类型：[SmartFixUserDetails.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SmartFixUserDetails.java)、[AttachmentInspectionService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/AttachmentInspectionService.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [RequestConfirmationController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestConfirmationController.java)

- 位置：`src/main/java/com/smartfix/request/controller/RequestConfirmationController.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：接收所属场景的 HTTP 请求，调用服务并选择页面、跳转或数据响应。
- 使用的项目类型：[SmartFixUserDetails.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SmartFixUserDetails.java)、[FinalReviewCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/FinalReviewCommand.java)、[RequestFeedbackCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestFeedbackCommand.java)、[RequestLifecycleActionCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestLifecycleActionCommand.java)、[RequestConfirmationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestConfirmationService.java)、[RequestPresentationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestPresentationService.java)、[RequestQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestQueryService.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [RequestQueryController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestQueryController.java)

- 位置：`src/main/java/com/smartfix/request/controller/RequestQueryController.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：接收所属场景的 HTTP 请求，调用服务并选择页面、跳转或数据响应。
- 使用的项目类型：[SmartFixUserDetails.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SmartFixUserDetails.java)、[MaintenanceCategory.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceCategory.java)、[RequestStage.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStage.java)、[RequestStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatus.java)、[UrgencyLevel.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/UrgencyLevel.java)、[FinalReviewCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/FinalReviewCommand.java)、[MaintenanceRequestDetailsResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/MaintenanceRequestDetailsResponse.java)、[RequestFeedbackCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestFeedbackCommand.java)、[RequestLifecycleActionCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestLifecycleActionCommand.java)、[RequestPresentationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestPresentationService.java)、[RequestQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestQueryService.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [RequestReviewController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestReviewController.java)

- 位置：`src/main/java/com/smartfix/request/controller/RequestReviewController.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：接收所属场景的 HTTP 请求，调用服务并选择页面、跳转或数据响应。
- 使用的项目类型：[SmartFixUserDetails.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SmartFixUserDetails.java)、[InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)、[UrgencyLevel.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/UrgencyLevel.java)、[RequestReviewCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestReviewCommand.java)、[RequestQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestQueryService.java)、[RequestReviewService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestReviewService.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [RequestSubmissionController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestSubmissionController.java)

- 位置：`src/main/java/com/smartfix/request/controller/RequestSubmissionController.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：接收所属场景的 HTTP 请求，调用服务并选择页面、跳转或数据响应。
- 使用的项目类型：[SmartFixUserDetails.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SmartFixUserDetails.java)、[InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)、[LocationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/LocationService.java)、[AttachmentProperties.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/config/AttachmentProperties.java)、[MaintenanceCategory.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceCategory.java)、[UrgencyLevel.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/UrgencyLevel.java)、[SubmitMaintenanceRequestCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/SubmitMaintenanceRequestCommand.java)、[RequestSubmissionService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestSubmissionService.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [Attachment.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/Attachment.java)

- 位置：`src/main/java/com/smartfix/request/domain/Attachment.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：保存所属业务对象的状态和规则，由服务创建或修改，并由仓库持久化。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[AttachmentRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/repository/AttachmentRepository.java)、[AttachmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/AttachmentService.java)

### [MaintenanceCategory.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceCategory.java)

- 位置：`src/main/java/com/smartfix/request/domain/MaintenanceCategory.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：限制该业务概念的合法取值，供规则判断、持久化或页面选项复用。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[TechnicianRecommendationResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/dto/TechnicianRecommendationResponse.java)、[TechnicianRecommendationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/TechnicianRecommendationService.java)、[OperationalReportResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/dto/OperationalReportResponse.java)、[OperationalReportService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/service/OperationalReportService.java)、[RequestQueryController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestQueryController.java)、[RequestSubmissionController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestSubmissionController.java)、[MaintenanceRequest.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceRequest.java)、[AdminRequestRow.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/AdminRequestRow.java)、[MaintenanceRequestDetailsResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/MaintenanceRequestDetailsResponse.java)、[MaintenanceRequestSummaryResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/MaintenanceRequestSummaryResponse.java)、[RequestSnapshotResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestSnapshotResponse.java)、[SubmitMaintenanceRequestCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/SubmitMaintenanceRequestCommand.java)、[RequestQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestQueryService.java)、[TechnicianProfileController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/controller/TechnicianProfileController.java)、[TechnicianProfile.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/domain/TechnicianProfile.java)、[TechnicianCandidateResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/dto/TechnicianCandidateResponse.java)、[TechnicianProfileResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/dto/TechnicianProfileResponse.java)、[UpdateTechnicianProfileCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/dto/UpdateTechnicianProfileCommand.java)、[TechnicianProfileRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/repository/TechnicianProfileRepository.java)、[TechnicianDirectoryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/service/TechnicianDirectoryService.java)

### [MaintenanceRequest.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceRequest.java)

- 位置：`src/main/java/com/smartfix/request/domain/MaintenanceRequest.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：保存所属业务对象的状态和规则，由服务创建或修改，并由仓库持久化。
- 使用的项目类型：[BusinessConflictException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/BusinessConflictException.java)、[MaintenanceCategory.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceCategory.java)、[RequestStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatus.java)、[RequestTransition.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestTransition.java)、[UrgencyLevel.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/UrgencyLevel.java)
- 引用它的项目文件：[SubmitMaintenanceRequestCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/SubmitMaintenanceRequestCommand.java)、[MaintenanceRequestRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/repository/MaintenanceRequestRepository.java)、[AttachmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/AttachmentService.java)、[RequestAccessService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestAccessService.java)、[RequestConfirmationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestConfirmationService.java)、[RequestLifecycleService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestLifecycleService.java)、[RequestQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestQueryService.java)、[RequestReadService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestReadService.java)、[RequestReviewService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestReviewService.java)、[RequestSubmissionService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestSubmissionService.java)

### [RequestFeedback.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestFeedback.java)

- 位置：`src/main/java/com/smartfix/request/domain/RequestFeedback.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：保存所属业务对象的状态和规则，由服务创建或修改，并由仓库持久化。
- 使用的项目类型：[InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)
- 引用它的项目文件：[RequestFeedbackRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/repository/RequestFeedbackRepository.java)、[RequestConfirmationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestConfirmationService.java)、[RequestPresentationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestPresentationService.java)

### [RequestStage.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStage.java)

- 位置：`src/main/java/com/smartfix/request/domain/RequestStage.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：将精细状态归入页面展示阶段。
- 使用的项目类型：[RequestStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatus.java)
- 引用它的项目文件：[AdminWorkspaceController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/controller/AdminWorkspaceController.java)、[RequestQueryController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestQueryController.java)、[RequestStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatus.java)、[RequestQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestQueryService.java)

### [RequestStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatus.java)

- 位置：`src/main/java/com/smartfix/request/domain/RequestStatus.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：限制该业务概念的合法取值，供规则判断、持久化或页面选项复用。
- 使用的项目类型：[RequestStage.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStage.java)
- 引用它的项目文件：[AssignmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/AssignmentService.java)、[DispatchPageService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/DispatchPageService.java)、[OperationalReportResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/dto/OperationalReportResponse.java)、[CampusMapStatusService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/service/CampusMapStatusService.java)、[OperationalReportService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/service/OperationalReportService.java)、[RequestQueryController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestQueryController.java)、[MaintenanceRequest.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceRequest.java)、[RequestStage.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStage.java)、[RequestStatusHistory.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatusHistory.java)、[RequestTransition.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestTransition.java)、[AdminRequestRow.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/AdminRequestRow.java)、[LocationRequestCount.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/LocationRequestCount.java)、[MaintenanceRequestDetailsResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/MaintenanceRequestDetailsResponse.java)、[MaintenanceRequestSummaryResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/MaintenanceRequestSummaryResponse.java)、[RequestSnapshotResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestSnapshotResponse.java)、[RequestStatusHistoryResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestStatusHistoryResponse.java)、[RequestTransitionResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestTransitionResponse.java)、[RequestWorkSummary.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestWorkSummary.java)、[RequestStatusChangedEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/event/RequestStatusChangedEvent.java)、[MaintenanceRequestRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/repository/MaintenanceRequestRepository.java)、[RequestConfirmationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestConfirmationService.java)、[RequestLifecycleService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestLifecycleService.java)、[RequestPresentationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestPresentationService.java)、[RequestQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestQueryService.java)、[RequestReadService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestReadService.java)、[RequestReviewService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestReviewService.java)、[SlaCalculationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/service/SlaCalculationService.java)、[WorkOrderQueueRow.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/dto/WorkOrderQueueRow.java)、[WorkOrderLifecycleParticipant.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderLifecycleParticipant.java)、[WorkOrderService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderService.java)

### [RequestStatusHistory.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatusHistory.java)

- 位置：`src/main/java/com/smartfix/request/domain/RequestStatusHistory.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：保存所属业务对象的状态和规则，由服务创建或修改，并由仓库持久化。
- 使用的项目类型：[RequestStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatus.java)
- 引用它的项目文件：[RequestStatusHistoryRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/repository/RequestStatusHistoryRepository.java)、[RequestLifecycleService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestLifecycleService.java)、[RequestQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestQueryService.java)

### [RequestTicketSequence.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestTicketSequence.java)

- 位置：`src/main/java/com/smartfix/request/domain/RequestTicketSequence.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：保存所属业务对象的状态和规则，由服务创建或修改，并由仓库持久化。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[RequestTicketSequenceRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/repository/RequestTicketSequenceRepository.java)、[RequestTicketNumberGenerator.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestTicketNumberGenerator.java)

### [RequestTransition.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestTransition.java)

- 位置：`src/main/java/com/smartfix/request/domain/RequestTransition.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：定义当前状态、目标状态、角色之间的允许规则。
- 使用的项目类型：[RequestStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatus.java)、[Role.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/Role.java)
- 引用它的项目文件：[MaintenanceRequest.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceRequest.java)、[RequestLifecycleService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestLifecycleService.java)

### [UrgencyLevel.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/UrgencyLevel.java)

- 位置：`src/main/java/com/smartfix/request/domain/UrgencyLevel.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：限制该业务概念的合法取值，供规则判断、持久化或页面选项复用。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[AssignTechnicianCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/dto/AssignTechnicianCommand.java)、[DispatchPageResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/dto/DispatchPageResponse.java)、[OperationalReportResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/dto/OperationalReportResponse.java)、[OperationalReportService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/service/OperationalReportService.java)、[RequestQueryController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestQueryController.java)、[RequestReviewController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestReviewController.java)、[RequestSubmissionController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestSubmissionController.java)、[MaintenanceRequest.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceRequest.java)、[AdminRequestRow.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/AdminRequestRow.java)、[MaintenanceRequestDetailsResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/MaintenanceRequestDetailsResponse.java)、[MaintenanceRequestSummaryResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/MaintenanceRequestSummaryResponse.java)、[RequestReviewCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestReviewCommand.java)、[RequestSnapshotResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestSnapshotResponse.java)、[RequestWorkSummary.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestWorkSummary.java)、[SubmitMaintenanceRequestCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/SubmitMaintenanceRequestCommand.java)、[RequestQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestQueryService.java)、[RequestReviewService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestReviewService.java)、[SlaController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/controller/SlaController.java)、[SlaPolicy.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/domain/SlaPolicy.java)、[SlaPolicyRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/repository/SlaPolicyRepository.java)、[SlaPolicyService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/service/SlaPolicyService.java)、[WorkOrderController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/controller/WorkOrderController.java)、[WorkOrderQueueRow.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/dto/WorkOrderQueueRow.java)、[WorkOrderService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderService.java)

### [AdminRequestRow.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/AdminRequestRow.java)

- 位置：`src/main/java/com/smartfix/request/dto/AdminRequestRow.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：[MaintenanceCategory.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceCategory.java)、[RequestStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatus.java)、[UrgencyLevel.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/UrgencyLevel.java)
- 引用它的项目文件：[RequestQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestQueryService.java)

### [AttachmentResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/AttachmentResponse.java)

- 位置：`src/main/java/com/smartfix/request/dto/AttachmentResponse.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[RequestPresentationResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestPresentationResponse.java)、[AttachmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/AttachmentService.java)

### [FinalReviewCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/FinalReviewCommand.java)

- 位置：`src/main/java/com/smartfix/request/dto/FinalReviewCommand.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：承载该操作的输入数据或表单值，交给 Controller 和 Service 校验、执行。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[RequestConfirmationController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestConfirmationController.java)、[RequestQueryController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestQueryController.java)

### [LocationRequestCount.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/LocationRequestCount.java)

- 位置：`src/main/java/com/smartfix/request/dto/LocationRequestCount.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：[RequestStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatus.java)
- 引用它的项目文件：[MaintenanceRequestRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/repository/MaintenanceRequestRepository.java)、[RequestReadService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestReadService.java)

### [MaintenanceRequestDetailsResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/MaintenanceRequestDetailsResponse.java)

- 位置：`src/main/java/com/smartfix/request/dto/MaintenanceRequestDetailsResponse.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：[MaintenanceCategory.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceCategory.java)、[RequestStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatus.java)、[UrgencyLevel.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/UrgencyLevel.java)、[RequestStatusHistoryResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestStatusHistoryResponse.java)
- 引用它的项目文件：[DispatchPageResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/dto/DispatchPageResponse.java)、[RequestQueryController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestQueryController.java)、[RequestQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestQueryService.java)

### [MaintenanceRequestSubmissionResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/MaintenanceRequestSubmissionResponse.java)

- 位置：`src/main/java/com/smartfix/request/dto/MaintenanceRequestSubmissionResponse.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[RequestSubmissionService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestSubmissionService.java)

### [MaintenanceRequestSummaryResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/MaintenanceRequestSummaryResponse.java)

- 位置：`src/main/java/com/smartfix/request/dto/MaintenanceRequestSummaryResponse.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：[MaintenanceCategory.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceCategory.java)、[RequestStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatus.java)、[UrgencyLevel.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/UrgencyLevel.java)
- 引用它的项目文件：[RequestQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestQueryService.java)

### [ReadableAttachment.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/ReadableAttachment.java)

- 位置：`src/main/java/com/smartfix/request/dto/ReadableAttachment.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[AttachmentController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/AttachmentController.java)、[AttachmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/AttachmentService.java)

### [RequestFeedbackCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestFeedbackCommand.java)

- 位置：`src/main/java/com/smartfix/request/dto/RequestFeedbackCommand.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：承载该操作的输入数据或表单值，交给 Controller 和 Service 校验、执行。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[RequestConfirmationController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestConfirmationController.java)、[RequestQueryController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestQueryController.java)

### [RequestLifecycleActionCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestLifecycleActionCommand.java)

- 位置：`src/main/java/com/smartfix/request/dto/RequestLifecycleActionCommand.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：承载该操作的输入数据或表单值，交给 Controller 和 Service 校验、执行。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[RequestConfirmationController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestConfirmationController.java)、[RequestQueryController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestQueryController.java)

### [RequestPresentationResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestPresentationResponse.java)

- 位置：`src/main/java/com/smartfix/request/dto/RequestPresentationResponse.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：[AttachmentResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/AttachmentResponse.java)、[RequestDetailContributor.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/spi/RequestDetailContributor.java)
- 引用它的项目文件：[RequestPresentationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestPresentationService.java)

### [RequestReviewCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestReviewCommand.java)

- 位置：`src/main/java/com/smartfix/request/dto/RequestReviewCommand.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：承载该操作的输入数据或表单值，交给 Controller 和 Service 校验、执行。
- 使用的项目类型：[UrgencyLevel.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/UrgencyLevel.java)
- 引用它的项目文件：[RequestReviewController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestReviewController.java)

### [RequestSnapshotResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestSnapshotResponse.java)

- 位置：`src/main/java/com/smartfix/request/dto/RequestSnapshotResponse.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：[MaintenanceCategory.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceCategory.java)、[RequestStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatus.java)、[UrgencyLevel.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/UrgencyLevel.java)
- 引用它的项目文件：[AssignmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/AssignmentService.java)、[OperationalReportService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/service/OperationalReportService.java)、[RequestReadService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestReadService.java)

### [RequestStatusHistoryResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestStatusHistoryResponse.java)

- 位置：`src/main/java/com/smartfix/request/dto/RequestStatusHistoryResponse.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：[RequestStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatus.java)
- 引用它的项目文件：[MaintenanceRequestDetailsResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/MaintenanceRequestDetailsResponse.java)、[RequestQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestQueryService.java)

### [RequestTransitionResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestTransitionResponse.java)

- 位置：`src/main/java/com/smartfix/request/dto/RequestTransitionResponse.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：[RequestStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatus.java)
- 引用它的项目文件：[RequestLifecycleService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestLifecycleService.java)、[RequestTransitionParticipant.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/spi/RequestTransitionParticipant.java)、[WorkOrderLifecycleParticipant.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderLifecycleParticipant.java)

### [RequestWorkSummary.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestWorkSummary.java)

- 位置：`src/main/java/com/smartfix/request/dto/RequestWorkSummary.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：[RequestStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatus.java)、[UrgencyLevel.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/UrgencyLevel.java)
- 引用它的项目文件：[RequestReadService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestReadService.java)、[WorkOrderService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderService.java)

### [StoredAttachment.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/StoredAttachment.java)

- 位置：`src/main/java/com/smartfix/request/dto/StoredAttachment.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[AttachmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/AttachmentService.java)、[AttachmentStorageService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/AttachmentStorageService.java)、[RequestSubmissionService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestSubmissionService.java)、[LocalAttachmentStorageService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/storage/LocalAttachmentStorageService.java)

### [SubmitMaintenanceRequestCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/SubmitMaintenanceRequestCommand.java)

- 位置：`src/main/java/com/smartfix/request/dto/SubmitMaintenanceRequestCommand.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：承载该操作的输入数据或表单值，交给 Controller 和 Service 校验、执行。
- 使用的项目类型：[MaintenanceCategory.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceCategory.java)、[MaintenanceRequest.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceRequest.java)、[UrgencyLevel.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/UrgencyLevel.java)
- 引用它的项目文件：[RequestSubmissionController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestSubmissionController.java)、[RequestSubmissionService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestSubmissionService.java)

### [UploadAttachmentCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/UploadAttachmentCommand.java)

- 位置：`src/main/java/com/smartfix/request/dto/UploadAttachmentCommand.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：承载该操作的输入数据或表单值，交给 Controller 和 Service 校验、执行。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [RequestStatusChangedEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/event/RequestStatusChangedEvent.java)

- 位置：`src/main/java/com/smartfix/request/event/RequestStatusChangedEvent.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：承载已经发生的业务事实；事件发布者与监听者通过该类型协作。
- 使用的项目类型：[RequestStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatus.java)
- 引用它的项目文件：[NotificationEventListener.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/listener/NotificationEventListener.java)、[RequestLifecycleService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestLifecycleService.java)、[SlaCalculationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/service/SlaCalculationService.java)

### [AttachmentRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/repository/AttachmentRepository.java)

- 位置：`src/main/java/com/smartfix/request/repository/AttachmentRepository.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：为所属业务对象提供数据库查询与持久化；通常由本模块服务调用。
- 使用的项目类型：[Attachment.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/Attachment.java)
- 引用它的项目文件：[AttachmentInspectionService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/AttachmentInspectionService.java)、[AttachmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/AttachmentService.java)、[RequestEvidenceService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestEvidenceService.java)

### [MaintenanceRequestRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/repository/MaintenanceRequestRepository.java)

- 位置：`src/main/java/com/smartfix/request/repository/MaintenanceRequestRepository.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：为所属业务对象提供数据库查询与持久化；通常由本模块服务调用。
- 使用的项目类型：[MaintenanceRequest.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceRequest.java)、[RequestStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatus.java)、[LocationRequestCount.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/LocationRequestCount.java)
- 引用它的项目文件：[RequestAccessService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestAccessService.java)、[RequestLifecycleService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestLifecycleService.java)、[RequestQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestQueryService.java)、[RequestReadService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestReadService.java)、[RequestReviewService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestReviewService.java)、[RequestSubmissionService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestSubmissionService.java)

### [RequestFeedbackRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/repository/RequestFeedbackRepository.java)

- 位置：`src/main/java/com/smartfix/request/repository/RequestFeedbackRepository.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：为所属业务对象提供数据库查询与持久化；通常由本模块服务调用。
- 使用的项目类型：[RequestFeedback.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestFeedback.java)
- 引用它的项目文件：[RequestConfirmationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestConfirmationService.java)、[RequestPresentationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestPresentationService.java)

### [RequestStatusHistoryRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/repository/RequestStatusHistoryRepository.java)

- 位置：`src/main/java/com/smartfix/request/repository/RequestStatusHistoryRepository.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：为所属业务对象提供数据库查询与持久化；通常由本模块服务调用。
- 使用的项目类型：[RequestStatusHistory.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatusHistory.java)
- 引用它的项目文件：[RequestLifecycleService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestLifecycleService.java)、[RequestQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestQueryService.java)

### [RequestTicketSequenceRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/repository/RequestTicketSequenceRepository.java)

- 位置：`src/main/java/com/smartfix/request/repository/RequestTicketSequenceRepository.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：为所属业务对象提供数据库查询与持久化；通常由本模块服务调用。
- 使用的项目类型：[RequestTicketSequence.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestTicketSequence.java)
- 引用它的项目文件：[RequestTicketNumberGenerator.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestTicketNumberGenerator.java)

### [AttachmentInspectionService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/AttachmentInspectionService.java)

- 位置：`src/main/java/com/smartfix/request/service/AttachmentInspectionService.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：只读核对旧的无引用附件文件。
- 使用的项目类型：[AttachmentProperties.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/config/AttachmentProperties.java)、[AttachmentRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/repository/AttachmentRepository.java)、[NotificationContactService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/NotificationContactService.java)
- 引用它的项目文件：[AttachmentInspectionController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/AttachmentInspectionController.java)

### [AttachmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/AttachmentService.java)

- 位置：`src/main/java/com/smartfix/request/service/AttachmentService.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：协调附件校验、存储、元数据和授权读取。
- 使用的项目类型：[ResourceNotFoundException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/ResourceNotFoundException.java)、[Attachment.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/Attachment.java)、[MaintenanceRequest.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceRequest.java)、[AttachmentResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/AttachmentResponse.java)、[ReadableAttachment.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/ReadableAttachment.java)、[StoredAttachment.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/StoredAttachment.java)、[AttachmentRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/repository/AttachmentRepository.java)、[AttachmentStorageService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/AttachmentStorageService.java)、[RequestAccessService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestAccessService.java)、[AttachmentValidator.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/validation/AttachmentValidator.java)
- 引用它的项目文件：[AttachmentController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/AttachmentController.java)、[RequestPresentationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestPresentationService.java)、[RequestSubmissionService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestSubmissionService.java)、[WorkOrderController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/controller/WorkOrderController.java)

### [AttachmentStorageService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/AttachmentStorageService.java)

- 位置：`src/main/java/com/smartfix/request/service/AttachmentStorageService.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：定义文件存储契约。
- 使用的项目类型：[StoredAttachment.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/StoredAttachment.java)
- 引用它的项目文件：[AttachmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/AttachmentService.java)、[LocalAttachmentStorageService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/storage/LocalAttachmentStorageService.java)

### [RequestAccessService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestAccessService.java)

- 位置：`src/main/java/com/smartfix/request/service/RequestAccessService.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：检查报修是否允许当前角色和身份读取。
- 使用的项目类型：[ResourceNotFoundException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/ResourceNotFoundException.java)、[MaintenanceRequest.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceRequest.java)、[MaintenanceRequestRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/repository/MaintenanceRequestRepository.java)、[RequestAssignmentAccessService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestAssignmentAccessService.java)、[AccountStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/AccountStatus.java)、[Role.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/Role.java)、[UserAccessResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/UserAccessResponse.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)
- 引用它的项目文件：[AttachmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/AttachmentService.java)、[RequestConfirmationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestConfirmationService.java)、[RequestLifecycleService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestLifecycleService.java)、[RequestPresentationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestPresentationService.java)、[RequestQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestQueryService.java)、[RequestReviewService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestReviewService.java)

### [RequestAssignmentAccessService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestAssignmentAccessService.java)

- 位置：`src/main/java/com/smartfix/request/service/RequestAssignmentAccessService.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：通过 ActiveAssignmentLookup 检查有效指派。
- 使用的项目类型：[BusinessConflictException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/BusinessConflictException.java)、[ActiveAssignmentLookup.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/spi/ActiveAssignmentLookup.java)、[AccountStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/AccountStatus.java)、[Role.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/Role.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)
- 引用它的项目文件：[HomeController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/web/HomeController.java)、[RequestAccessService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestAccessService.java)、[RequestLifecycleService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestLifecycleService.java)、[RequestPresentationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestPresentationService.java)、[TechnicianWorkloadService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/service/TechnicianWorkloadService.java)、[WorkOrderLifecycleParticipant.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderLifecycleParticipant.java)、[WorkOrderService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderService.java)

### [RequestConfirmationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestConfirmationService.java)

- 位置：`src/main/java/com/smartfix/request/service/RequestConfirmationService.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：承接用户验收、返修、取消、评分及管理员终审。
- 使用的项目类型：[BusinessConflictException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/BusinessConflictException.java)、[ResourceNotFoundException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/ResourceNotFoundException.java)、[MaintenanceRequest.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceRequest.java)、[RequestFeedback.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestFeedback.java)、[RequestStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatus.java)、[RequestFeedbackRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/repository/RequestFeedbackRepository.java)、[RequestAccessService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestAccessService.java)、[RequestLifecycleService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestLifecycleService.java)、[Role.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/Role.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)
- 引用它的项目文件：[RequestConfirmationController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestConfirmationController.java)

### [RequestEvidenceService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestEvidenceService.java)

- 位置：`src/main/java/com/smartfix/request/service/RequestEvidenceService.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：为其他模块检查附件确实属于指定报修。
- 使用的项目类型：[ResourceNotFoundException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/ResourceNotFoundException.java)、[AttachmentRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/repository/AttachmentRepository.java)
- 引用它的项目文件：[WorkOrderService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderService.java)

### [RequestLifecycleService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestLifecycleService.java)

- 位置：`src/main/java/com/smartfix/request/service/RequestLifecycleService.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：统一检查转换，调用参与者，更新主状态、历史并发布事件。
- 使用的项目类型：[BusinessConflictException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/BusinessConflictException.java)、[InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)、[ResourceNotFoundException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/ResourceNotFoundException.java)、[RequestWorkflowProperties.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/config/RequestWorkflowProperties.java)、[MaintenanceRequest.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceRequest.java)、[RequestStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatus.java)、[RequestStatusHistory.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatusHistory.java)、[RequestTransition.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestTransition.java)、[RequestTransitionResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestTransitionResponse.java)、[RequestStatusChangedEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/event/RequestStatusChangedEvent.java)、[MaintenanceRequestRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/repository/MaintenanceRequestRepository.java)、[RequestStatusHistoryRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/repository/RequestStatusHistoryRepository.java)、[RequestAccessService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestAccessService.java)、[RequestAssignmentAccessService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestAssignmentAccessService.java)、[RequestTransitionParticipant.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/spi/RequestTransitionParticipant.java)、[AccountStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/AccountStatus.java)、[Role.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/Role.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)
- 引用它的项目文件：[AssignmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/AssignmentService.java)、[RequestConfirmationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestConfirmationService.java)、[RequestReviewService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestReviewService.java)、[RequestSubmissionService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestSubmissionService.java)、[WorkOrderService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderService.java)

### [RequestPresentationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestPresentationService.java)

- 位置：`src/main/java/com/smartfix/request/service/RequestPresentationService.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：组合附件、详情扩展、当前指派、评分和可用操作。
- 使用的项目类型：[RequestFeedback.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestFeedback.java)、[RequestStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatus.java)、[RequestPresentationResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestPresentationResponse.java)、[RequestFeedbackRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/repository/RequestFeedbackRepository.java)、[AttachmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/AttachmentService.java)、[RequestAccessService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestAccessService.java)、[RequestAssignmentAccessService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestAssignmentAccessService.java)、[RequestDetailContributor.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/spi/RequestDetailContributor.java)、[Role.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/Role.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)
- 引用它的项目文件：[RequestConfirmationController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestConfirmationController.java)、[RequestQueryController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestQueryController.java)

### [RequestQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestQueryService.java)

- 位置：`src/main/java/com/smartfix/request/service/RequestQueryService.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：面向页面查询报修列表、详情、管理队列和统计。
- 使用的项目类型：[InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)、[LocationResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/LocationResponse.java)、[LocationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/LocationService.java)、[MaintenanceCategory.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceCategory.java)、[MaintenanceRequest.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceRequest.java)、[RequestStage.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStage.java)、[RequestStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatus.java)、[RequestStatusHistory.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatusHistory.java)、[UrgencyLevel.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/UrgencyLevel.java)、[AdminRequestRow.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/AdminRequestRow.java)、[MaintenanceRequestDetailsResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/MaintenanceRequestDetailsResponse.java)、[MaintenanceRequestSummaryResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/MaintenanceRequestSummaryResponse.java)、[RequestStatusHistoryResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestStatusHistoryResponse.java)、[MaintenanceRequestRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/repository/MaintenanceRequestRepository.java)、[RequestStatusHistoryRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/repository/RequestStatusHistoryRepository.java)、[RequestAccessService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestAccessService.java)、[Role.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/Role.java)
- 引用它的项目文件：[HomeController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/web/HomeController.java)、[DispatchPageService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/DispatchPageService.java)、[AdminWorkspaceController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/controller/AdminWorkspaceController.java)、[RequestConfirmationController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestConfirmationController.java)、[RequestQueryController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestQueryController.java)、[RequestReviewController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestReviewController.java)、[WorkOrderController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/controller/WorkOrderController.java)

### [RequestReadService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestReadService.java)

- 位置：`src/main/java/com/smartfix/request/service/RequestReadService.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：向其他模块提供报修只读数据契约。
- 使用的项目类型：[InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)、[ResourceNotFoundException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/ResourceNotFoundException.java)、[MaintenanceRequest.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceRequest.java)、[RequestStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatus.java)、[LocationRequestCount.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/LocationRequestCount.java)、[RequestSnapshotResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestSnapshotResponse.java)、[RequestWorkSummary.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestWorkSummary.java)、[MaintenanceRequestRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/repository/MaintenanceRequestRepository.java)
- 引用它的项目文件：[AssignmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/AssignmentService.java)、[DispatchPageService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/DispatchPageService.java)、[CampusMapStatusService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/service/CampusMapStatusService.java)、[OperationalReportService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/service/OperationalReportService.java)、[SlaCalculationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/service/SlaCalculationService.java)、[WorkOrderService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderService.java)

### [RequestReviewService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestReviewService.java)

- 位置：`src/main/java/com/smartfix/request/service/RequestReviewService.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：审核最终优先级，按规则推进审核或驳回。
- 使用的项目类型：[InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)、[ResourceNotFoundException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/ResourceNotFoundException.java)、[MaintenanceRequest.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceRequest.java)、[RequestStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatus.java)、[UrgencyLevel.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/UrgencyLevel.java)、[MaintenanceRequestRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/repository/MaintenanceRequestRepository.java)、[RequestAccessService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestAccessService.java)、[RequestLifecycleService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestLifecycleService.java)、[AccountStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/AccountStatus.java)、[Role.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/Role.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)
- 引用它的项目文件：[AssignmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/AssignmentService.java)、[RequestReviewController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestReviewController.java)

### [RequestSubmissionService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestSubmissionService.java)

- 位置：`src/main/java/com/smartfix/request/service/RequestSubmissionService.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：先校验并存附件，再以事务保存报修、初始历史、附件元数据；确认回滚后补偿新文件。
- 使用的项目类型：[InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)、[ResourceNotFoundException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/ResourceNotFoundException.java)、[LocationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/LocationService.java)、[MaintenanceRequest.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceRequest.java)、[MaintenanceRequestSubmissionResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/MaintenanceRequestSubmissionResponse.java)、[StoredAttachment.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/StoredAttachment.java)、[SubmitMaintenanceRequestCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/SubmitMaintenanceRequestCommand.java)、[MaintenanceRequestRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/repository/MaintenanceRequestRepository.java)、[AttachmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/AttachmentService.java)、[RequestLifecycleService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestLifecycleService.java)、[RequestTicketNumberGenerator.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestTicketNumberGenerator.java)、[AccountStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/AccountStatus.java)、[Role.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/Role.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)
- 引用它的项目文件：[RequestSubmissionController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestSubmissionController.java)

### [RequestTicketNumberGenerator.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestTicketNumberGenerator.java)

- 位置：`src/main/java/com/smartfix/request/service/RequestTicketNumberGenerator.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：通过编号序列仓库生成报修业务编号。
- 使用的项目类型：[RequestTicketSequence.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestTicketSequence.java)、[RequestTicketSequenceRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/repository/RequestTicketSequenceRepository.java)
- 引用它的项目文件：[RequestSubmissionService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestSubmissionService.java)

### [ActiveAssignmentLookup.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/spi/ActiveAssignmentLookup.java)

- 位置：`src/main/java/com/smartfix/request/spi/ActiveAssignmentLookup.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：由 request 定义当前指派查询契约。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[RequestAssignmentLookupAdapter.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/RequestAssignmentLookupAdapter.java)、[RequestAssignmentAccessService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestAssignmentAccessService.java)

### [RequestDetailContributor.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/spi/RequestDetailContributor.java)

- 位置：`src/main/java/com/smartfix/request/spi/RequestDetailContributor.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：定义其他模块向共享报修详情补充展示信息的契约。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[RequestPresentationResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestPresentationResponse.java)、[RequestPresentationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestPresentationService.java)、[WorkOrderDetailContributor.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderDetailContributor.java)

### [RequestTransitionParticipant.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/spi/RequestTransitionParticipant.java)

- 位置：`src/main/java/com/smartfix/request/spi/RequestTransitionParticipant.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：定义同一生命周期事务中的前置检查和后置同步契约。
- 使用的项目类型：[RequestTransitionResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestTransitionResponse.java)
- 引用它的项目文件：[RequestLifecycleService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestLifecycleService.java)、[WorkOrderLifecycleParticipant.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderLifecycleParticipant.java)

### [LocalAttachmentStorageService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/storage/LocalAttachmentStorageService.java)

- 位置：`src/main/java/com/smartfix/request/storage/LocalAttachmentStorageService.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：实现本地文件存储、读取和清理。
- 使用的项目类型：[AttachmentProperties.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/config/AttachmentProperties.java)、[StoredAttachment.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/StoredAttachment.java)、[AttachmentStorageService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/AttachmentStorageService.java)、[AttachmentValidator.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/validation/AttachmentValidator.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [AttachmentValidator.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/validation/AttachmentValidator.java)

- 位置：`src/main/java/com/smartfix/request/validation/AttachmentValidator.java`
- 运行场景：场景07提交与附件、08查询详情、09审核、10派单联动、12验收至15取消返修。
- 职责：执行上传类型、大小、数量、尺寸等规则。
- 使用的项目类型：[AttachmentProperties.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/config/AttachmentProperties.java)
- 引用它的项目文件：[AttachmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/AttachmentService.java)、[LocalAttachmentStorageService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/storage/LocalAttachmentStorageService.java)

## technician 模块

### [AdminTechnicianController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/controller/AdminTechnicianController.java)

- 位置：`src/main/java/com/smartfix/technician/controller/AdminTechnicianController.java`
- 运行场景：场景05工程师资料、10候选工程师与工作量。
- 职责：接收所属场景的 HTTP 请求，调用服务并选择页面、跳转或数据响应。
- 使用的项目类型：[SmartFixUserDetails.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SmartFixUserDetails.java)、[TechnicianProfileResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/dto/TechnicianProfileResponse.java)、[TechnicianDirectoryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/service/TechnicianDirectoryService.java)、[Role.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/Role.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [TechnicianProfileController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/controller/TechnicianProfileController.java)

- 位置：`src/main/java/com/smartfix/technician/controller/TechnicianProfileController.java`
- 运行场景：场景05工程师资料、10候选工程师与工作量。
- 职责：接收所属场景的 HTTP 请求，调用服务并选择页面、跳转或数据响应。
- 使用的项目类型：[SmartFixUserDetails.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SmartFixUserDetails.java)、[BusinessConflictException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/BusinessConflictException.java)、[InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)、[LocationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/LocationService.java)、[MaintenanceCategory.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceCategory.java)、[AvailabilityStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/domain/AvailabilityStatus.java)、[UpdateTechnicianProfileCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/dto/UpdateTechnicianProfileCommand.java)、[TechnicianDirectoryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/service/TechnicianDirectoryService.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [AvailabilityStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/domain/AvailabilityStatus.java)

- 位置：`src/main/java/com/smartfix/technician/domain/AvailabilityStatus.java`
- 运行场景：场景05工程师资料、10候选工程师与工作量。
- 职责：限制该业务概念的合法取值，供规则判断、持久化或页面选项复用。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[TechnicianRecommendationResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/dto/TechnicianRecommendationResponse.java)、[TechnicianRecommendationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/TechnicianRecommendationService.java)、[TechnicianProfileController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/controller/TechnicianProfileController.java)、[TechnicianProfile.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/domain/TechnicianProfile.java)、[TechnicianCandidateResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/dto/TechnicianCandidateResponse.java)、[TechnicianProfileResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/dto/TechnicianProfileResponse.java)、[UpdateTechnicianProfileCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/dto/UpdateTechnicianProfileCommand.java)、[TechnicianProfileRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/repository/TechnicianProfileRepository.java)、[TechnicianDirectoryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/service/TechnicianDirectoryService.java)

### [TechnicianProfile.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/domain/TechnicianProfile.java)

- 位置：`src/main/java/com/smartfix/technician/domain/TechnicianProfile.java`
- 运行场景：场景05工程师资料、10候选工程师与工作量。
- 职责：保存所属业务对象的状态和规则，由服务创建或修改，并由仓库持久化。
- 使用的项目类型：[MaintenanceCategory.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceCategory.java)、[AvailabilityStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/domain/AvailabilityStatus.java)
- 引用它的项目文件：[TechnicianProfileRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/repository/TechnicianProfileRepository.java)、[TechnicianDirectoryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/service/TechnicianDirectoryService.java)

### [TechnicianCandidateResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/dto/TechnicianCandidateResponse.java)

- 位置：`src/main/java/com/smartfix/technician/dto/TechnicianCandidateResponse.java`
- 运行场景：场景05工程师资料、10候选工程师与工作量。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：[MaintenanceCategory.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceCategory.java)、[AvailabilityStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/domain/AvailabilityStatus.java)
- 引用它的项目文件：[TechnicianDirectoryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/service/TechnicianDirectoryService.java)

### [TechnicianProfileResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/dto/TechnicianProfileResponse.java)

- 位置：`src/main/java/com/smartfix/technician/dto/TechnicianProfileResponse.java`
- 运行场景：场景05工程师资料、10候选工程师与工作量。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：[MaintenanceCategory.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceCategory.java)、[AvailabilityStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/domain/AvailabilityStatus.java)
- 引用它的项目文件：[AdminTechnicianController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/controller/AdminTechnicianController.java)、[TechnicianDirectoryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/service/TechnicianDirectoryService.java)

### [UpdateTechnicianProfileCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/dto/UpdateTechnicianProfileCommand.java)

- 位置：`src/main/java/com/smartfix/technician/dto/UpdateTechnicianProfileCommand.java`
- 运行场景：场景05工程师资料、10候选工程师与工作量。
- 职责：承载该操作的输入数据或表单值，交给 Controller 和 Service 校验、执行。
- 使用的项目类型：[MaintenanceCategory.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceCategory.java)、[AvailabilityStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/domain/AvailabilityStatus.java)
- 引用它的项目文件：[TechnicianProfileController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/controller/TechnicianProfileController.java)、[TechnicianDirectoryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/service/TechnicianDirectoryService.java)

### [TechnicianProfileRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/repository/TechnicianProfileRepository.java)

- 位置：`src/main/java/com/smartfix/technician/repository/TechnicianProfileRepository.java`
- 运行场景：场景05工程师资料、10候选工程师与工作量。
- 职责：为所属业务对象提供数据库查询与持久化；通常由本模块服务调用。
- 使用的项目类型：[MaintenanceCategory.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceCategory.java)、[AvailabilityStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/domain/AvailabilityStatus.java)、[TechnicianProfile.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/domain/TechnicianProfile.java)
- 引用它的项目文件：[TechnicianDirectoryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/service/TechnicianDirectoryService.java)

### [TechnicianDirectoryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/service/TechnicianDirectoryService.java)

- 位置：`src/main/java/com/smartfix/technician/service/TechnicianDirectoryService.java`
- 运行场景：场景05工程师资料、10候选工程师与工作量。
- 职责：管理工程师资料，并提供手工指派候选账号。
- 使用的项目类型：[BusinessConflictException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/BusinessConflictException.java)、[InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)、[ResourceNotFoundException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/ResourceNotFoundException.java)、[LocationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/LocationService.java)、[MaintenanceCategory.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceCategory.java)、[AvailabilityStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/domain/AvailabilityStatus.java)、[TechnicianProfile.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/domain/TechnicianProfile.java)、[TechnicianCandidateResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/dto/TechnicianCandidateResponse.java)、[TechnicianProfileResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/dto/TechnicianProfileResponse.java)、[UpdateTechnicianProfileCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/dto/UpdateTechnicianProfileCommand.java)、[TechnicianProfileRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/repository/TechnicianProfileRepository.java)、[AccountStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/AccountStatus.java)、[Role.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/Role.java)、[UserSummaryResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/UserSummaryResponse.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)
- 引用它的项目文件：[AssignmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/AssignmentService.java)、[DispatchPageService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/DispatchPageService.java)、[TechnicianRecommendationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/TechnicianRecommendationService.java)、[AdminTechnicianController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/controller/AdminTechnicianController.java)、[TechnicianProfileController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/controller/TechnicianProfileController.java)

### [TechnicianWorkloadService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/service/TechnicianWorkloadService.java)

- 位置：`src/main/java/com/smartfix/technician/service/TechnicianWorkloadService.java`
- 运行场景：场景05工程师资料、10候选工程师与工作量。
- 职责：读取工程师开放工单数量等工作量信息。
- 使用的项目类型：[BusinessConflictException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/BusinessConflictException.java)、[InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)、[RequestAssignmentAccessService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestAssignmentAccessService.java)、[WorkOrderService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderService.java)
- 引用它的项目文件：[DispatchPageService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/DispatchPageService.java)、[TechnicianRecommendationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/TechnicianRecommendationService.java)、[DashboardService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/service/DashboardService.java)

## dispatch 模块

### [DispatchController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/controller/DispatchController.java)

- 位置：`src/main/java/com/smartfix/dispatch/controller/DispatchController.java`
- 运行场景：场景10指派、14改派与撤销。
- 职责：接收所属场景的 HTTP 请求，调用服务并选择页面、跳转或数据响应。
- 使用的项目类型：[SmartFixUserDetails.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SmartFixUserDetails.java)、[BusinessConflictException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/BusinessConflictException.java)、[InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)、[AssignTechnicianCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/dto/AssignTechnicianCommand.java)、[ReassignTechnicianCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/dto/ReassignTechnicianCommand.java)、[WithdrawAssignmentCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/dto/WithdrawAssignmentCommand.java)、[AssignmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/AssignmentService.java)、[DispatchPageService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/DispatchPageService.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [Assignment.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/domain/Assignment.java)

- 位置：`src/main/java/com/smartfix/dispatch/domain/Assignment.java`
- 运行场景：场景10指派、14改派与撤销。
- 职责：保存所属业务对象的状态和规则，由服务创建或修改，并由仓库持久化。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[AssignmentResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/dto/AssignmentResponse.java)、[AssignmentRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/repository/AssignmentRepository.java)、[AssignmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/AssignmentService.java)

### [AssignmentResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/dto/AssignmentResponse.java)

- 位置：`src/main/java/com/smartfix/dispatch/dto/AssignmentResponse.java`
- 运行场景：场景10指派、14改派与撤销。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：[Assignment.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/domain/Assignment.java)
- 引用它的项目文件：[DispatchPageResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/dto/DispatchPageResponse.java)、[AssignmentReadService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/AssignmentReadService.java)、[AssignmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/AssignmentService.java)

### [AssignTechnicianCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/dto/AssignTechnicianCommand.java)

- 位置：`src/main/java/com/smartfix/dispatch/dto/AssignTechnicianCommand.java`
- 运行场景：场景10指派、14改派与撤销。
- 职责：承载该操作的输入数据或表单值，交给 Controller 和 Service 校验、执行。
- 使用的项目类型：[UrgencyLevel.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/UrgencyLevel.java)
- 引用它的项目文件：[DispatchController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/controller/DispatchController.java)、[AssignmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/AssignmentService.java)

### [DispatchPageResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/dto/DispatchPageResponse.java)

- 位置：`src/main/java/com/smartfix/dispatch/dto/DispatchPageResponse.java`
- 运行场景：场景10指派、14改派与撤销。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：[AssignmentResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/dto/AssignmentResponse.java)、[UrgencyLevel.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/UrgencyLevel.java)、[MaintenanceRequestDetailsResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/MaintenanceRequestDetailsResponse.java)
- 引用它的项目文件：[DispatchPageService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/DispatchPageService.java)

### [ReassignTechnicianCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/dto/ReassignTechnicianCommand.java)

- 位置：`src/main/java/com/smartfix/dispatch/dto/ReassignTechnicianCommand.java`
- 运行场景：场景10指派、14改派与撤销。
- 职责：承载该操作的输入数据或表单值，交给 Controller 和 Service 校验、执行。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[DispatchController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/controller/DispatchController.java)、[AssignmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/AssignmentService.java)

### [TechnicianRecommendationResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/dto/TechnicianRecommendationResponse.java)

- 位置：`src/main/java/com/smartfix/dispatch/dto/TechnicianRecommendationResponse.java`
- 运行场景：场景10指派、14改派与撤销。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：[MaintenanceCategory.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceCategory.java)、[AvailabilityStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/domain/AvailabilityStatus.java)
- 引用它的项目文件：[TechnicianRecommendationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/TechnicianRecommendationService.java)

### [WithdrawAssignmentCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/dto/WithdrawAssignmentCommand.java)

- 位置：`src/main/java/com/smartfix/dispatch/dto/WithdrawAssignmentCommand.java`
- 运行场景：场景10指派、14改派与撤销。
- 职责：承载该操作的输入数据或表单值，交给 Controller 和 Service 校验、执行。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[DispatchController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/controller/DispatchController.java)、[AssignmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/AssignmentService.java)

### [AssignmentCreatedEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/event/AssignmentCreatedEvent.java)

- 位置：`src/main/java/com/smartfix/dispatch/event/AssignmentCreatedEvent.java`
- 运行场景：场景10指派、14改派与撤销。
- 职责：承载已经发生的业务事实；事件发布者与监听者通过该类型协作。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[AssignmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/AssignmentService.java)

### [AssignmentWithdrawnEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/event/AssignmentWithdrawnEvent.java)

- 位置：`src/main/java/com/smartfix/dispatch/event/AssignmentWithdrawnEvent.java`
- 运行场景：场景10指派、14改派与撤销。
- 职责：承载已经发生的业务事实；事件发布者与监听者通过该类型协作。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[AssignmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/AssignmentService.java)

### [AssignmentRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/repository/AssignmentRepository.java)

- 位置：`src/main/java/com/smartfix/dispatch/repository/AssignmentRepository.java`
- 运行场景：场景10指派、14改派与撤销。
- 职责：为所属业务对象提供数据库查询与持久化；通常由本模块服务调用。
- 使用的项目类型：[Assignment.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/domain/Assignment.java)
- 引用它的项目文件：[AssignmentReadService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/AssignmentReadService.java)、[AssignmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/AssignmentService.java)

### [AssignmentReadService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/AssignmentReadService.java)

- 位置：`src/main/java/com/smartfix/dispatch/service/AssignmentReadService.java`
- 运行场景：场景10指派、14改派与撤销。
- 职责：提供当前有效指派等只读数据。
- 使用的项目类型：[AssignmentResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/dto/AssignmentResponse.java)、[AssignmentRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/repository/AssignmentRepository.java)
- 引用它的项目文件：[AssignmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/AssignmentService.java)、[DispatchPageService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/DispatchPageService.java)、[RequestAssignmentLookupAdapter.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/RequestAssignmentLookupAdapter.java)

### [AssignmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/AssignmentService.java)

- 位置：`src/main/java/com/smartfix/dispatch/service/AssignmentService.java`
- 运行场景：场景10指派、14改派与撤销。
- 职责：执行首次指派、改派、撤销，协调审核、有效指派及生命周期。
- 使用的项目类型：[BusinessConflictException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/BusinessConflictException.java)、[InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)、[Assignment.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/domain/Assignment.java)、[AssignmentResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/dto/AssignmentResponse.java)、[AssignTechnicianCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/dto/AssignTechnicianCommand.java)、[ReassignTechnicianCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/dto/ReassignTechnicianCommand.java)、[WithdrawAssignmentCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/dto/WithdrawAssignmentCommand.java)、[AssignmentCreatedEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/event/AssignmentCreatedEvent.java)、[AssignmentWithdrawnEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/event/AssignmentWithdrawnEvent.java)、[AssignmentRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/repository/AssignmentRepository.java)、[AssignmentReadService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/AssignmentReadService.java)、[RequestStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatus.java)、[RequestSnapshotResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestSnapshotResponse.java)、[RequestLifecycleService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestLifecycleService.java)、[RequestReadService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestReadService.java)、[RequestReviewService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestReviewService.java)、[TechnicianDirectoryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/service/TechnicianDirectoryService.java)、[AccountStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/AccountStatus.java)、[Role.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/Role.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)
- 引用它的项目文件：[DispatchController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/controller/DispatchController.java)

### [DispatchPageService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/DispatchPageService.java)

- 位置：`src/main/java/com/smartfix/dispatch/service/DispatchPageService.java`
- 运行场景：场景10指派、14改派与撤销。
- 职责：组合报修、当前指派、启用工程师和工作量供指派页展示。
- 使用的项目类型：[DispatchPageResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/dto/DispatchPageResponse.java)、[AssignmentReadService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/AssignmentReadService.java)、[RequestStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatus.java)、[RequestQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestQueryService.java)、[RequestReadService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestReadService.java)、[TechnicianDirectoryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/service/TechnicianDirectoryService.java)、[TechnicianWorkloadService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/service/TechnicianWorkloadService.java)、[AccountStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/AccountStatus.java)、[Role.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/Role.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)
- 引用它的项目文件：[DispatchController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/controller/DispatchController.java)

### [RequestAssignmentLookupAdapter.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/RequestAssignmentLookupAdapter.java)

- 位置：`src/main/java/com/smartfix/dispatch/service/RequestAssignmentLookupAdapter.java`
- 运行场景：场景10指派、14改派与撤销。
- 职责：把 dispatch 的指派查询适配为 request 的接口。
- 使用的项目类型：[AssignmentReadService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/AssignmentReadService.java)、[ActiveAssignmentLookup.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/spi/ActiveAssignmentLookup.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [TechnicianRecommendationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/service/TechnicianRecommendationService.java)

- 位置：`src/main/java/com/smartfix/dispatch/service/TechnicianRecommendationService.java`
- 运行场景：场景10指派、14改派与撤销。
- 职责：保留可选推荐逻辑；当前手动指派不以推荐结果为硬门槛。
- 使用的项目类型：[TechnicianRecommendationResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/dispatch/dto/TechnicianRecommendationResponse.java)、[MaintenanceCategory.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceCategory.java)、[AvailabilityStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/domain/AvailabilityStatus.java)、[TechnicianDirectoryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/service/TechnicianDirectoryService.java)、[TechnicianWorkloadService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/service/TechnicianWorkloadService.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

## workorder 模块

### [WorkOrderController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/controller/WorkOrderController.java)

- 位置：`src/main/java/com/smartfix/workorder/controller/WorkOrderController.java`
- 运行场景：场景11工程师执行、12审核关闭、14改派、15取消。
- 职责：接收所属场景的 HTTP 请求，调用服务并选择页面、跳转或数据响应。
- 使用的项目类型：[SmartFixUserDetails.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SmartFixUserDetails.java)、[BusinessConflictException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/BusinessConflictException.java)、[InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)、[LocationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/LocationService.java)、[UrgencyLevel.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/UrgencyLevel.java)、[AttachmentService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/AttachmentService.java)、[RequestQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestQueryService.java)、[WorkOrderStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/domain/WorkOrderStatus.java)、[CompleteWorkOrderCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/dto/CompleteWorkOrderCommand.java)、[RepairRecordCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/dto/RepairRecordCommand.java)、[WorkOrderQueueView.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/dto/WorkOrderQueueView.java)、[WorkOrderService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderService.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [RepairRecord.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/domain/RepairRecord.java)

- 位置：`src/main/java/com/smartfix/workorder/domain/RepairRecord.java`
- 运行场景：场景11工程师执行、12审核关闭、14改派、15取消。
- 职责：保存所属业务对象的状态和规则，由服务创建或修改，并由仓库持久化。
- 使用的项目类型：[InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)、[RepairRecordCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/dto/RepairRecordCommand.java)
- 引用它的项目文件：[RepairRecordRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/repository/RepairRecordRepository.java)、[WorkOrderService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderService.java)

### [WorkOrder.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/domain/WorkOrder.java)

- 位置：`src/main/java/com/smartfix/workorder/domain/WorkOrder.java`
- 运行场景：场景11工程师执行、12审核关闭、14改派、15取消。
- 职责：保存所属业务对象的状态和规则，由服务创建或修改，并由仓库持久化。
- 使用的项目类型：[BusinessConflictException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/BusinessConflictException.java)、[InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)、[WorkOrderStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/domain/WorkOrderStatus.java)
- 引用它的项目文件：[WorkOrderRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/repository/WorkOrderRepository.java)、[WorkOrderLifecycleParticipant.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderLifecycleParticipant.java)、[WorkOrderService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderService.java)

### [WorkOrderStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/domain/WorkOrderStatus.java)

- 位置：`src/main/java/com/smartfix/workorder/domain/WorkOrderStatus.java`
- 运行场景：场景11工程师执行、12审核关闭、14改派、15取消。
- 职责：限制该业务概念的合法取值，供规则判断、持久化或页面选项复用。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[WorkOrderController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/controller/WorkOrderController.java)、[WorkOrder.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/domain/WorkOrder.java)、[WorkOrderQueueRow.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/dto/WorkOrderQueueRow.java)、[WorkOrderResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/dto/WorkOrderResponse.java)、[WorkOrderRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/repository/WorkOrderRepository.java)、[WorkOrderLifecycleParticipant.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderLifecycleParticipant.java)、[WorkOrderService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderService.java)

### [CompleteWorkOrderCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/dto/CompleteWorkOrderCommand.java)

- 位置：`src/main/java/com/smartfix/workorder/dto/CompleteWorkOrderCommand.java`
- 运行场景：场景11工程师执行、12审核关闭、14改派、15取消。
- 职责：承载该操作的输入数据或表单值，交给 Controller 和 Service 校验、执行。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[WorkOrderController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/controller/WorkOrderController.java)、[WorkOrderService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderService.java)

### [RepairRecordCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/dto/RepairRecordCommand.java)

- 位置：`src/main/java/com/smartfix/workorder/dto/RepairRecordCommand.java`
- 运行场景：场景11工程师执行、12审核关闭、14改派、15取消。
- 职责：承载该操作的输入数据或表单值，交给 Controller 和 Service 校验、执行。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[WorkOrderController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/controller/WorkOrderController.java)、[RepairRecord.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/domain/RepairRecord.java)、[WorkOrderService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderService.java)

### [RepairRecordResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/dto/RepairRecordResponse.java)

- 位置：`src/main/java/com/smartfix/workorder/dto/RepairRecordResponse.java`
- 运行场景：场景11工程师执行、12审核关闭、14改派、15取消。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[WorkOrderService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderService.java)

### [WorkOrderQueueRow.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/dto/WorkOrderQueueRow.java)

- 位置：`src/main/java/com/smartfix/workorder/dto/WorkOrderQueueRow.java`
- 运行场景：场景11工程师执行、12审核关闭、14改派、15取消。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：[RequestStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatus.java)、[UrgencyLevel.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/UrgencyLevel.java)、[WorkOrderStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/domain/WorkOrderStatus.java)
- 引用它的项目文件：[WorkOrderService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderService.java)

### [WorkOrderQueueView.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/dto/WorkOrderQueueView.java)

- 位置：`src/main/java/com/smartfix/workorder/dto/WorkOrderQueueView.java`
- 运行场景：场景11工程师执行、12审核关闭、14改派、15取消。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[WorkOrderController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/controller/WorkOrderController.java)、[WorkOrderService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderService.java)

### [WorkOrderResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/dto/WorkOrderResponse.java)

- 位置：`src/main/java/com/smartfix/workorder/dto/WorkOrderResponse.java`
- 运行场景：场景11工程师执行、12审核关闭、14改派、15取消。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：[WorkOrderStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/domain/WorkOrderStatus.java)
- 引用它的项目文件：[WorkOrderService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderService.java)

### [WorkOrderCompletedEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/event/WorkOrderCompletedEvent.java)

- 位置：`src/main/java/com/smartfix/workorder/event/WorkOrderCompletedEvent.java`
- 运行场景：场景11工程师执行、12审核关闭、14改派、15取消。
- 职责：承载已经发生的业务事实；事件发布者与监听者通过该类型协作。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[NotificationEventListener.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/listener/NotificationEventListener.java)、[WorkOrderService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderService.java)

### [RepairRecordRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/repository/RepairRecordRepository.java)

- 位置：`src/main/java/com/smartfix/workorder/repository/RepairRecordRepository.java`
- 运行场景：场景11工程师执行、12审核关闭、14改派、15取消。
- 职责：为所属业务对象提供数据库查询与持久化；通常由本模块服务调用。
- 使用的项目类型：[RepairRecord.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/domain/RepairRecord.java)
- 引用它的项目文件：[WorkOrderService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderService.java)

### [WorkOrderRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/repository/WorkOrderRepository.java)

- 位置：`src/main/java/com/smartfix/workorder/repository/WorkOrderRepository.java`
- 运行场景：场景11工程师执行、12审核关闭、14改派、15取消。
- 职责：为所属业务对象提供数据库查询与持久化；通常由本模块服务调用。
- 使用的项目类型：[WorkOrder.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/domain/WorkOrder.java)、[WorkOrderStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/domain/WorkOrderStatus.java)
- 引用它的项目文件：[WorkOrderDetailContributor.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderDetailContributor.java)、[WorkOrderLifecycleParticipant.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderLifecycleParticipant.java)、[WorkOrderService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderService.java)

### [WorkOrderDetailContributor.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderDetailContributor.java)

- 位置：`src/main/java/com/smartfix/workorder/service/WorkOrderDetailContributor.java`
- 运行场景：场景11工程师执行、12审核关闭、14改派、15取消。
- 职责：把工单编号、执行状态和解决方案补入共享报修详情。
- 使用的项目类型：[RequestDetailContributor.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/spi/RequestDetailContributor.java)、[WorkOrderRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/repository/WorkOrderRepository.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [WorkOrderLifecycleParticipant.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderLifecycleParticipant.java)

- 位置：`src/main/java/com/smartfix/workorder/service/WorkOrderLifecycleParticipant.java`
- 运行场景：场景11工程师执行、12审核关闭、14改派、15取消。
- 职责：在报修转换前检查工单前提，转换后创建或同步工单状态。
- 使用的项目类型：[BusinessConflictException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/BusinessConflictException.java)、[RequestStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatus.java)、[RequestTransitionResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestTransitionResponse.java)、[RequestAssignmentAccessService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestAssignmentAccessService.java)、[RequestTransitionParticipant.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/spi/RequestTransitionParticipant.java)、[WorkOrder.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/domain/WorkOrder.java)、[WorkOrderStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/domain/WorkOrderStatus.java)、[WorkOrderRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/repository/WorkOrderRepository.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [WorkOrderService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/service/WorkOrderService.java)

- 位置：`src/main/java/com/smartfix/workorder/service/WorkOrderService.java`
- 运行场景：场景11工程师执行、12审核关闭、14改派、15取消。
- 职责：工程师列表、读取、接单、记录维修和提交完成。
- 使用的项目类型：[BusinessConflictException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/BusinessConflictException.java)、[InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)、[ResourceNotFoundException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/ResourceNotFoundException.java)、[RequestStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatus.java)、[UrgencyLevel.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/UrgencyLevel.java)、[RequestWorkSummary.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestWorkSummary.java)、[RequestAssignmentAccessService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestAssignmentAccessService.java)、[RequestEvidenceService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestEvidenceService.java)、[RequestLifecycleService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestLifecycleService.java)、[RequestReadService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestReadService.java)、[AccountStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/AccountStatus.java)、[Role.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/Role.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)、[RepairRecord.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/domain/RepairRecord.java)、[WorkOrder.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/domain/WorkOrder.java)、[WorkOrderStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/domain/WorkOrderStatus.java)、[CompleteWorkOrderCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/dto/CompleteWorkOrderCommand.java)、[RepairRecordCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/dto/RepairRecordCommand.java)、[RepairRecordResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/dto/RepairRecordResponse.java)、[WorkOrderQueueRow.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/dto/WorkOrderQueueRow.java)、[WorkOrderQueueView.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/dto/WorkOrderQueueView.java)、[WorkOrderResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/dto/WorkOrderResponse.java)、[WorkOrderCompletedEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/event/WorkOrderCompletedEvent.java)、[RepairRecordRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/repository/RepairRecordRepository.java)、[WorkOrderRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/repository/WorkOrderRepository.java)
- 引用它的项目文件：[TechnicianWorkloadService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/service/TechnicianWorkloadService.java)、[WorkOrderController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/controller/WorkOrderController.java)

## notification 模块

### [DeliveryProperties.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/config/DeliveryProperties.java)

- 位置：`src/main/java/com/smartfix/notification/config/DeliveryProperties.java`
- 运行场景：场景16消息生成投递、17已读与管理员重试。
- 职责：提供投递开关、批量、重试和邮件配置。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[DeliveryAdministrationController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/controller/DeliveryAdministrationController.java)、[DeliveryDispatcher.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/DeliveryDispatcher.java)、[DeliveryOutboxService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/DeliveryOutboxService.java)、[DeliveryWorker.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/DeliveryWorker.java)

### [DeliveryScheduling.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/config/DeliveryScheduling.java)

- 位置：`src/main/java/com/smartfix/notification/config/DeliveryScheduling.java`
- 运行场景：场景16消息生成投递、17已读与管理员重试。
- 职责：以定时任务触发消息扫描和 SLA 扫描；受配置开关控制。
- 使用的项目类型：[DeliveryDispatcher.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/DeliveryDispatcher.java)、[SlaScanner.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/service/SlaScanner.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [DeliveryAdministrationController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/controller/DeliveryAdministrationController.java)

- 位置：`src/main/java/com/smartfix/notification/controller/DeliveryAdministrationController.java`
- 运行场景：场景16消息生成投递、17已读与管理员重试。
- 职责：接收所属场景的 HTTP 请求，调用服务并选择页面、跳转或数据响应。
- 使用的项目类型：[SmartFixUserDetails.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SmartFixUserDetails.java)、[DeliveryProperties.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/config/DeliveryProperties.java)、[DeliveryAdministrationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/DeliveryAdministrationService.java)、[DeliveryDispatcher.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/DeliveryDispatcher.java)、[NotificationContactService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/NotificationContactService.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [NotificationController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/controller/NotificationController.java)

- 位置：`src/main/java/com/smartfix/notification/controller/NotificationController.java`
- 运行场景：场景16消息生成投递、17已读与管理员重试。
- 职责：接收所属场景的 HTTP 请求，调用服务并选择页面、跳转或数据响应。
- 使用的项目类型：[SmartFixUserDetails.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SmartFixUserDetails.java)、[NotificationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/NotificationService.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [DeliveryTask.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/domain/DeliveryTask.java)

- 位置：`src/main/java/com/smartfix/notification/domain/DeliveryTask.java`
- 运行场景：场景16消息生成投递、17已读与管理员重试。
- 职责：保存所属业务对象的状态和规则，由服务创建或修改，并由仓库持久化。
- 使用的项目类型：[Notification.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/domain/Notification.java)
- 引用它的项目文件：[DeliveryTaskRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/repository/DeliveryTaskRepository.java)、[DeliveryAdministrationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/DeliveryAdministrationService.java)、[DeliveryOutboxService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/DeliveryOutboxService.java)、[EmailDeliveryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/EmailDeliveryService.java)

### [Notification.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/domain/Notification.java)

- 位置：`src/main/java/com/smartfix/notification/domain/Notification.java`
- 运行场景：场景16消息生成投递、17已读与管理员重试。
- 职责：保存所属业务对象的状态和规则，由服务创建或修改，并由仓库持久化。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[DeliveryTask.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/domain/DeliveryTask.java)、[NotificationRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/repository/NotificationRepository.java)、[NotificationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/NotificationService.java)

### [DeliveryQueuedEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/event/DeliveryQueuedEvent.java)

- 位置：`src/main/java/com/smartfix/notification/event/DeliveryQueuedEvent.java`
- 运行场景：场景16消息生成投递、17已读与管理员重试。
- 职责：承载已经发生的业务事实；事件发布者与监听者通过该类型协作。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[DeliveryDispatcher.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/DeliveryDispatcher.java)、[DeliveryOutboxService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/DeliveryOutboxService.java)

### [CommunityNotificationListener.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/listener/CommunityNotificationListener.java)

- 位置：`src/main/java/com/smartfix/notification/listener/CommunityNotificationListener.java`
- 运行场景：场景16消息生成投递、17已读与管理员重试。
- 职责：订阅回答、采纳、内容隐藏事件并写投递任务。
- 使用的项目类型：[CommunityContentStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityContentStatus.java)、[CommunityAnswerAcceptedEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/event/CommunityAnswerAcceptedEvent.java)、[CommunityAnswerCreatedEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/event/CommunityAnswerCreatedEvent.java)、[CommunityContentHiddenEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/event/CommunityContentHiddenEvent.java)、[DeliveryOutboxService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/DeliveryOutboxService.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [NotificationEventListener.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/listener/NotificationEventListener.java)

- 位置：`src/main/java/com/smartfix/notification/listener/NotificationEventListener.java`
- 运行场景：场景16消息生成投递、17已读与管理员重试。
- 职责：订阅报修状态及工单完成事件，在提交前写投递意图。
- 使用的项目类型：[DeliveryOutboxService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/DeliveryOutboxService.java)、[RequestStatusChangedEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/event/RequestStatusChangedEvent.java)、[WorkOrderCompletedEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/event/WorkOrderCompletedEvent.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [SlaNotificationListener.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/listener/SlaNotificationListener.java)

- 位置：`src/main/java/com/smartfix/notification/listener/SlaNotificationListener.java`
- 运行场景：场景16消息生成投递、17已读与管理员重试。
- 职责：把临期、超期事件转换为通知任务。
- 使用的项目类型：[DeliveryOutboxService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/DeliveryOutboxService.java)、[SlaBreachedEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/event/SlaBreachedEvent.java)、[SlaDueSoonEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/event/SlaDueSoonEvent.java)、[NotificationContactService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/NotificationContactService.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [DeliveryTaskInsertRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/repository/DeliveryTaskInsertRepository.java)

- 位置：`src/main/java/com/smartfix/notification/repository/DeliveryTaskInsertRepository.java`
- 运行场景：场景16消息生成投递、17已读与管理员重试。
- 职责：实现投递意图的幂等持久化。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[DeliveryOutboxService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/DeliveryOutboxService.java)

### [DeliveryTaskRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/repository/DeliveryTaskRepository.java)

- 位置：`src/main/java/com/smartfix/notification/repository/DeliveryTaskRepository.java`
- 运行场景：场景16消息生成投递、17已读与管理员重试。
- 职责：为所属业务对象提供数据库查询与持久化；通常由本模块服务调用。
- 使用的项目类型：[DeliveryTask.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/domain/DeliveryTask.java)
- 引用它的项目文件：[DeliveryAdministrationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/DeliveryAdministrationService.java)、[DeliveryDispatcher.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/DeliveryDispatcher.java)、[DeliveryOutboxService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/DeliveryOutboxService.java)、[DeliveryWorker.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/DeliveryWorker.java)

### [NotificationInsertRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/repository/NotificationInsertRepository.java)

- 位置：`src/main/java/com/smartfix/notification/repository/NotificationInsertRepository.java`
- 运行场景：场景16消息生成投递、17已读与管理员重试。
- 职责：定义通知幂等插入契约。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[PostgresNotificationInsertRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/repository/PostgresNotificationInsertRepository.java)、[NotificationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/NotificationService.java)

### [NotificationRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/repository/NotificationRepository.java)

- 位置：`src/main/java/com/smartfix/notification/repository/NotificationRepository.java`
- 运行场景：场景16消息生成投递、17已读与管理员重试。
- 职责：为所属业务对象提供数据库查询与持久化；通常由本模块服务调用。
- 使用的项目类型：[Notification.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/domain/Notification.java)
- 引用它的项目文件：[NotificationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/NotificationService.java)

### [PostgresNotificationInsertRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/repository/PostgresNotificationInsertRepository.java)

- 位置：`src/main/java/com/smartfix/notification/repository/PostgresNotificationInsertRepository.java`
- 运行场景：场景16消息生成投递、17已读与管理员重试。
- 职责：实现数据库方言相关的去重插入。
- 使用的项目类型：[NotificationInsertRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/repository/NotificationInsertRepository.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [DeliveryAdministrationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/DeliveryAdministrationService.java)

- 位置：`src/main/java/com/smartfix/notification/service/DeliveryAdministrationService.java`
- 运行场景：场景16消息生成投递、17已读与管理员重试。
- 职责：管理员查询投递状态和重试，并记录审计。
- 使用的项目类型：[AuditService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/audit/service/AuditService.java)、[BusinessConflictException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/BusinessConflictException.java)、[ResourceNotFoundException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/ResourceNotFoundException.java)、[DeliveryTask.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/domain/DeliveryTask.java)、[DeliveryTaskRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/repository/DeliveryTaskRepository.java)、[NotificationContactService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/NotificationContactService.java)
- 引用它的项目文件：[DeliveryAdministrationController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/controller/DeliveryAdministrationController.java)

### [DeliveryDispatcher.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/DeliveryDispatcher.java)

- 位置：`src/main/java/com/smartfix/notification/service/DeliveryDispatcher.java`
- 运行场景：场景16消息生成投递、17已读与管理员重试。
- 职责：提交后唤醒或定时读取到期任务，逐项调用 Worker。
- 使用的项目类型：[DeliveryProperties.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/config/DeliveryProperties.java)、[DeliveryQueuedEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/event/DeliveryQueuedEvent.java)、[DeliveryTaskRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/repository/DeliveryTaskRepository.java)、[DeliveryWorker.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/DeliveryWorker.java)
- 引用它的项目文件：[DeliveryScheduling.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/config/DeliveryScheduling.java)、[DeliveryAdministrationController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/controller/DeliveryAdministrationController.java)

### [DeliveryOutboxService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/DeliveryOutboxService.java)

- 位置：`src/main/java/com/smartfix/notification/service/DeliveryOutboxService.java`
- 运行场景：场景16消息生成投递、17已读与管理员重试。
- 职责：在业务事务中持久化各渠道投递任务并发布排队事件。
- 使用的项目类型：[DeliveryProperties.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/config/DeliveryProperties.java)、[DeliveryTask.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/domain/DeliveryTask.java)、[DeliveryQueuedEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/event/DeliveryQueuedEvent.java)、[DeliveryTaskInsertRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/repository/DeliveryTaskInsertRepository.java)、[DeliveryTaskRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/repository/DeliveryTaskRepository.java)
- 引用它的项目文件：[CommunityNotificationListener.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/listener/CommunityNotificationListener.java)、[NotificationEventListener.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/listener/NotificationEventListener.java)、[SlaNotificationListener.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/listener/SlaNotificationListener.java)

### [DeliveryWorker.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/DeliveryWorker.java)

- 位置：`src/main/java/com/smartfix/notification/service/DeliveryWorker.java`
- 运行场景：场景16消息生成投递、17已读与管理员重试。
- 职责：独立事务锁定任务，投递、更新状态及有限重试。
- 使用的项目类型：[AuditService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/audit/service/AuditService.java)、[DeliveryProperties.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/config/DeliveryProperties.java)、[DeliveryTaskRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/repository/DeliveryTaskRepository.java)、[EmailDeliveryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/EmailDeliveryService.java)、[NotificationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/NotificationService.java)
- 引用它的项目文件：[DeliveryDispatcher.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/DeliveryDispatcher.java)

### [EmailDeliveryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/EmailDeliveryService.java)

- 位置：`src/main/java/com/smartfix/notification/service/EmailDeliveryService.java`
- 运行场景：场景16消息生成投递、17已读与管理员重试。
- 职责：向配置的实际通知地址发送可选邮件。
- 使用的项目类型：[DeliveryTask.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/domain/DeliveryTask.java)、[NotificationContactService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/NotificationContactService.java)
- 引用它的项目文件：[DeliveryWorker.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/DeliveryWorker.java)

### [NotificationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/NotificationService.java)

- 位置：`src/main/java/com/smartfix/notification/service/NotificationService.java`
- 运行场景：场景16消息生成投递、17已读与管理员重试。
- 职责：接收者范围内查询通知、未读数量及标记已读。
- 使用的项目类型：[ResourceNotFoundException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/ResourceNotFoundException.java)、[Notification.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/domain/Notification.java)、[NotificationInsertRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/repository/NotificationInsertRepository.java)、[NotificationRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/repository/NotificationRepository.java)
- 引用它的项目文件：[NotificationNavigationAdvice.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/web/NotificationNavigationAdvice.java)、[NotificationController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/controller/NotificationController.java)、[DeliveryWorker.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/DeliveryWorker.java)

## sla 模块

### [SlaController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/controller/SlaController.java)

- 位置：`src/main/java/com/smartfix/sla/controller/SlaController.java`
- 运行场景：场景18策略配置、状态计时与后台扫描。
- 职责：接收所属场景的 HTTP 请求，调用服务并选择页面、跳转或数据响应。
- 使用的项目类型：[SmartFixUserDetails.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SmartFixUserDetails.java)、[UrgencyLevel.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/UrgencyLevel.java)、[SlaPhase.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/domain/SlaPhase.java)、[SlaPolicy.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/domain/SlaPolicy.java)、[SlaPolicyService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/service/SlaPolicyService.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [SlaPhase.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/domain/SlaPhase.java)

- 位置：`src/main/java/com/smartfix/sla/domain/SlaPhase.java`
- 运行场景：场景18策略配置、状态计时与后台扫描。
- 职责：限制该业务概念的合法取值，供规则判断、持久化或页面选项复用。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[SlaController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/controller/SlaController.java)、[SlaPolicy.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/domain/SlaPolicy.java)、[SlaState.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/domain/SlaState.java)、[SlaPolicyRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/repository/SlaPolicyRepository.java)、[SlaCalculationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/service/SlaCalculationService.java)、[SlaPolicyService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/service/SlaPolicyService.java)

### [SlaPolicy.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/domain/SlaPolicy.java)

- 位置：`src/main/java/com/smartfix/sla/domain/SlaPolicy.java`
- 运行场景：场景18策略配置、状态计时与后台扫描。
- 职责：保存所属业务对象的状态和规则，由服务创建或修改，并由仓库持久化。
- 使用的项目类型：[UrgencyLevel.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/UrgencyLevel.java)、[SlaPhase.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/domain/SlaPhase.java)
- 引用它的项目文件：[SlaController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/controller/SlaController.java)、[SlaState.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/domain/SlaState.java)、[SlaPolicyRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/repository/SlaPolicyRepository.java)、[SlaCalculationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/service/SlaCalculationService.java)、[SlaPolicyService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/service/SlaPolicyService.java)

### [SlaState.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/domain/SlaState.java)

- 位置：`src/main/java/com/smartfix/sla/domain/SlaState.java`
- 运行场景：场景18策略配置、状态计时与后台扫描。
- 职责：保存所属业务对象的状态和规则，由服务创建或修改，并由仓库持久化。
- 使用的项目类型：[SlaPhase.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/domain/SlaPhase.java)、[SlaPolicy.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/domain/SlaPolicy.java)
- 引用它的项目文件：[SlaStateRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/repository/SlaStateRepository.java)、[SlaCalculationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/service/SlaCalculationService.java)、[SlaPolicyService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/service/SlaPolicyService.java)

### [SlaBreachedEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/event/SlaBreachedEvent.java)

- 位置：`src/main/java/com/smartfix/sla/event/SlaBreachedEvent.java`
- 运行场景：场景18策略配置、状态计时与后台扫描。
- 职责：承载已经发生的业务事实；事件发布者与监听者通过该类型协作。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[SlaNotificationListener.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/listener/SlaNotificationListener.java)、[SlaScanWorker.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/service/SlaScanWorker.java)

### [SlaDueSoonEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/event/SlaDueSoonEvent.java)

- 位置：`src/main/java/com/smartfix/sla/event/SlaDueSoonEvent.java`
- 运行场景：场景18策略配置、状态计时与后台扫描。
- 职责：承载已经发生的业务事实；事件发布者与监听者通过该类型协作。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[SlaNotificationListener.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/listener/SlaNotificationListener.java)、[SlaScanWorker.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/service/SlaScanWorker.java)

### [SlaPolicyRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/repository/SlaPolicyRepository.java)

- 位置：`src/main/java/com/smartfix/sla/repository/SlaPolicyRepository.java`
- 运行场景：场景18策略配置、状态计时与后台扫描。
- 职责：为所属业务对象提供数据库查询与持久化；通常由本模块服务调用。
- 使用的项目类型：[UrgencyLevel.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/UrgencyLevel.java)、[SlaPhase.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/domain/SlaPhase.java)、[SlaPolicy.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/domain/SlaPolicy.java)
- 引用它的项目文件：[SlaCalculationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/service/SlaCalculationService.java)、[SlaPolicyService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/service/SlaPolicyService.java)

### [SlaStateRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/repository/SlaStateRepository.java)

- 位置：`src/main/java/com/smartfix/sla/repository/SlaStateRepository.java`
- 运行场景：场景18策略配置、状态计时与后台扫描。
- 职责：为所属业务对象提供数据库查询与持久化；通常由本模块服务调用。
- 使用的项目类型：[SlaState.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/domain/SlaState.java)
- 引用它的项目文件：[SlaCalculationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/service/SlaCalculationService.java)、[SlaPolicyService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/service/SlaPolicyService.java)、[SlaScanner.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/service/SlaScanner.java)、[SlaScanWorker.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/service/SlaScanWorker.java)

### [SlaCalculationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/service/SlaCalculationService.java)

- 位置：`src/main/java/com/smartfix/sla/service/SlaCalculationService.java`
- 运行场景：场景18策略配置、状态计时与后台扫描。
- 职责：订阅报修状态事件，按策略建立、完成、暂停或重启时限状态。
- 使用的项目类型：[RequestStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatus.java)、[RequestStatusChangedEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/event/RequestStatusChangedEvent.java)、[RequestReadService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestReadService.java)、[SlaPhase.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/domain/SlaPhase.java)、[SlaPolicy.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/domain/SlaPolicy.java)、[SlaState.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/domain/SlaState.java)、[SlaPolicyRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/repository/SlaPolicyRepository.java)、[SlaStateRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/repository/SlaStateRepository.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [SlaPolicyService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/service/SlaPolicyService.java)

- 位置：`src/main/java/com/smartfix/sla/service/SlaPolicyService.java`
- 运行场景：场景18策略配置、状态计时与后台扫描。
- 职责：管理优先级与阶段对应的启用策略和时长。
- 使用的项目类型：[AuditService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/audit/service/AuditService.java)、[InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)、[UrgencyLevel.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/UrgencyLevel.java)、[SlaPhase.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/domain/SlaPhase.java)、[SlaPolicy.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/domain/SlaPolicy.java)、[SlaState.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/domain/SlaState.java)、[SlaPolicyRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/repository/SlaPolicyRepository.java)、[SlaStateRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/repository/SlaStateRepository.java)、[NotificationContactService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/NotificationContactService.java)
- 引用它的项目文件：[SlaController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/controller/SlaController.java)

### [SlaScanner.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/service/SlaScanner.java)

- 位置：`src/main/java/com/smartfix/sla/service/SlaScanner.java`
- 运行场景：场景18策略配置、状态计时与后台扫描。
- 职责：批量读取待检查的 SLA 状态，逐项交给扫描 Worker。
- 使用的项目类型：[SlaStateRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/repository/SlaStateRepository.java)、[SlaScanWorker.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/service/SlaScanWorker.java)
- 引用它的项目文件：[DeliveryScheduling.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/config/DeliveryScheduling.java)

### [SlaScanWorker.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/service/SlaScanWorker.java)

- 位置：`src/main/java/com/smartfix/sla/service/SlaScanWorker.java`
- 运行场景：场景18策略配置、状态计时与后台扫描。
- 职责：检查一项 SLA，发布临期或超期事件并维护去重状态。
- 使用的项目类型：[SlaBreachedEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/event/SlaBreachedEvent.java)、[SlaDueSoonEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/event/SlaDueSoonEvent.java)、[SlaStateRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/repository/SlaStateRepository.java)
- 引用它的项目文件：[SlaScanner.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/service/SlaScanner.java)

## facility 模块

### [FacilityController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/controller/FacilityController.java)

- 位置：`src/main/java/com/smartfix/facility/controller/FacilityController.java`
- 运行场景：场景07报修地点、19设施管理、20地图。
- 职责：接收所属场景的 HTTP 请求，调用服务并选择页面、跳转或数据响应。
- 使用的项目类型：[InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)、[Facility.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/domain/Facility.java)、[FacilityStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/domain/FacilityStatus.java)、[CreateFacilityCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/CreateFacilityCommand.java)、[FacilityService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/FacilityService.java)、[LocationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/LocationService.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [Facility.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/domain/Facility.java)

- 位置：`src/main/java/com/smartfix/facility/domain/Facility.java`
- 运行场景：场景07报修地点、19设施管理、20地图。
- 职责：保存所属业务对象的状态和规则，由服务创建或修改，并由仓库持久化。
- 使用的项目类型：[FacilityStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/domain/FacilityStatus.java)、[Location.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/domain/Location.java)
- 引用它的项目文件：[FacilityController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/controller/FacilityController.java)、[FacilityRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/repository/FacilityRepository.java)、[CampusMapService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/CampusMapService.java)、[FacilityService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/FacilityService.java)

### [FacilityStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/domain/FacilityStatus.java)

- 位置：`src/main/java/com/smartfix/facility/domain/FacilityStatus.java`
- 运行场景：场景07报修地点、19设施管理、20地图。
- 职责：限制该业务概念的合法取值，供规则判断、持久化或页面选项复用。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[FacilityController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/controller/FacilityController.java)、[Facility.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/domain/Facility.java)、[CampusGeography.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/CampusGeography.java)、[CampusMapFacilityResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/CampusMapFacilityResponse.java)、[CampusMapResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/CampusMapResponse.java)、[CreateFacilityCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/CreateFacilityCommand.java)、[FacilityResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/FacilityResponse.java)、[LocationFacilityCount.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/LocationFacilityCount.java)、[CampusGeographyService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/CampusGeographyService.java)、[CampusMapService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/CampusMapService.java)、[FacilityService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/FacilityService.java)、[CampusMapController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/controller/CampusMapController.java)

### [Location.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/domain/Location.java)

- 位置：`src/main/java/com/smartfix/facility/domain/Location.java`
- 运行场景：场景07报修地点、19设施管理、20地图。
- 职责：保存所属业务对象的状态和规则，由服务创建或修改，并由仓库持久化。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[Facility.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/domain/Facility.java)、[LocationRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/repository/LocationRepository.java)、[CampusMapService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/CampusMapService.java)、[LocationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/LocationService.java)

### [CampusBuilding.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/CampusBuilding.java)

- 位置：`src/main/java/com/smartfix/facility/dto/CampusBuilding.java`
- 运行场景：场景07报修地点、19设施管理、20地图。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[CampusGeography.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/CampusGeography.java)、[CampusGeographyService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/CampusGeographyService.java)

### [CampusGeography.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/CampusGeography.java)

- 位置：`src/main/java/com/smartfix/facility/dto/CampusGeography.java`
- 运行场景：场景07报修地点、19设施管理、20地图。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：[FacilityStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/domain/FacilityStatus.java)、[CampusBuilding.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/CampusBuilding.java)
- 引用它的项目文件：[CampusGeographyService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/CampusGeographyService.java)

### [CampusMapBuildingResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/CampusMapBuildingResponse.java)

- 位置：`src/main/java/com/smartfix/facility/dto/CampusMapBuildingResponse.java`
- 运行场景：场景07报修地点、19设施管理、20地图。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[CampusMapResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/CampusMapResponse.java)、[CampusMapService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/CampusMapService.java)

### [CampusMapFacilityResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/CampusMapFacilityResponse.java)

- 位置：`src/main/java/com/smartfix/facility/dto/CampusMapFacilityResponse.java`
- 运行场景：场景07报修地点、19设施管理、20地图。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：[FacilityStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/domain/FacilityStatus.java)
- 引用它的项目文件：[CampusMapResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/CampusMapResponse.java)、[CampusMapService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/CampusMapService.java)

### [CampusMapResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/CampusMapResponse.java)

- 位置：`src/main/java/com/smartfix/facility/dto/CampusMapResponse.java`
- 运行场景：场景07报修地点、19设施管理、20地图。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：[FacilityStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/domain/FacilityStatus.java)、[CampusMapBuildingResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/CampusMapBuildingResponse.java)、[CampusMapFacilityResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/CampusMapFacilityResponse.java)
- 引用它的项目文件：[CampusMapService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/CampusMapService.java)

### [CreateFacilityCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/CreateFacilityCommand.java)

- 位置：`src/main/java/com/smartfix/facility/dto/CreateFacilityCommand.java`
- 运行场景：场景07报修地点、19设施管理、20地图。
- 职责：承载该操作的输入数据或表单值，交给 Controller 和 Service 校验、执行。
- 使用的项目类型：[FacilityStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/domain/FacilityStatus.java)
- 引用它的项目文件：[FacilityController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/controller/FacilityController.java)、[FacilityService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/FacilityService.java)

### [FacilityResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/FacilityResponse.java)

- 位置：`src/main/java/com/smartfix/facility/dto/FacilityResponse.java`
- 运行场景：场景07报修地点、19设施管理、20地图。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：[FacilityStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/domain/FacilityStatus.java)
- 引用它的项目文件：[FacilityService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/FacilityService.java)

### [LocationFacilityCount.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/LocationFacilityCount.java)

- 位置：`src/main/java/com/smartfix/facility/dto/LocationFacilityCount.java`
- 运行场景：场景07报修地点、19设施管理、20地图。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：[FacilityStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/domain/FacilityStatus.java)
- 引用它的项目文件：[FacilityRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/repository/FacilityRepository.java)

### [LocationResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/LocationResponse.java)

- 位置：`src/main/java/com/smartfix/facility/dto/LocationResponse.java`
- 运行场景：场景07报修地点、19设施管理、20地图。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[LocationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/LocationService.java)、[RequestQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestQueryService.java)

### [FacilityRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/repository/FacilityRepository.java)

- 位置：`src/main/java/com/smartfix/facility/repository/FacilityRepository.java`
- 运行场景：场景07报修地点、19设施管理、20地图。
- 职责：为所属业务对象提供数据库查询与持久化；通常由本模块服务调用。
- 使用的项目类型：[Facility.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/domain/Facility.java)、[LocationFacilityCount.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/LocationFacilityCount.java)
- 引用它的项目文件：[CampusGeographyService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/CampusGeographyService.java)、[CampusMapService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/CampusMapService.java)、[FacilityService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/FacilityService.java)

### [LocationRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/repository/LocationRepository.java)

- 位置：`src/main/java/com/smartfix/facility/repository/LocationRepository.java`
- 运行场景：场景07报修地点、19设施管理、20地图。
- 职责：为所属业务对象提供数据库查询与持久化；通常由本模块服务调用。
- 使用的项目类型：[Location.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/domain/Location.java)
- 引用它的项目文件：[CampusGeographyService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/CampusGeographyService.java)、[CampusMapService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/CampusMapService.java)、[LocationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/LocationService.java)

### [CampusGeographyService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/CampusGeographyService.java)

- 位置：`src/main/java/com/smartfix/facility/service/CampusGeographyService.java`
- 运行场景：场景07报修地点、19设施管理、20地图。
- 职责：读取建筑目录与地点映射，组合设施聚合计数。
- 使用的项目类型：[FacilityStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/domain/FacilityStatus.java)、[CampusBuilding.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/CampusBuilding.java)、[CampusGeography.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/CampusGeography.java)、[FacilityRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/repository/FacilityRepository.java)、[LocationRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/repository/LocationRepository.java)、[CampusMapService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/CampusMapService.java)
- 引用它的项目文件：[CampusMapStatusService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/service/CampusMapStatusService.java)

### [CampusMapService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/CampusMapService.java)

- 位置：`src/main/java/com/smartfix/facility/service/CampusMapService.java`
- 运行场景：场景07报修地点、19设施管理、20地图。
- 职责：提供地图所需目录与设施读取能力。
- 使用的项目类型：[InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)、[ResourceNotFoundException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/ResourceNotFoundException.java)、[Facility.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/domain/Facility.java)、[FacilityStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/domain/FacilityStatus.java)、[Location.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/domain/Location.java)、[CampusMapBuildingResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/CampusMapBuildingResponse.java)、[CampusMapFacilityResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/CampusMapFacilityResponse.java)、[CampusMapResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/CampusMapResponse.java)、[FacilityRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/repository/FacilityRepository.java)、[LocationRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/repository/LocationRepository.java)、[AccountStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/AccountStatus.java)、[Role.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/Role.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)
- 引用它的项目文件：[CampusGeographyService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/CampusGeographyService.java)、[CampusMapController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/controller/CampusMapController.java)

### [FacilityService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/FacilityService.java)

- 位置：`src/main/java/com/smartfix/facility/service/FacilityService.java`
- 运行场景：场景07报修地点、19设施管理、20地图。
- 职责：参与所属场景的协作类型，具体依赖见下方文件关联。
- 使用的项目类型：[InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)、[ResourceNotFoundException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/ResourceNotFoundException.java)、[Facility.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/domain/Facility.java)、[FacilityStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/domain/FacilityStatus.java)、[CreateFacilityCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/CreateFacilityCommand.java)、[FacilityResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/FacilityResponse.java)、[FacilityRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/repository/FacilityRepository.java)、[LocationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/LocationService.java)
- 引用它的项目文件：[FacilityController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/controller/FacilityController.java)

### [LocationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/LocationService.java)

- 位置：`src/main/java/com/smartfix/facility/service/LocationService.java`
- 运行场景：场景07报修地点、19设施管理、20地图。
- 职责：参与所属场景的协作类型，具体依赖见下方文件关联。
- 使用的项目类型：[InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)、[ResourceNotFoundException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/ResourceNotFoundException.java)、[Location.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/domain/Location.java)、[LocationResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/dto/LocationResponse.java)、[LocationRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/repository/LocationRepository.java)
- 引用它的项目文件：[FacilityController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/controller/FacilityController.java)、[FacilityService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/FacilityService.java)、[RequestSubmissionController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/controller/RequestSubmissionController.java)、[RequestQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestQueryService.java)、[RequestSubmissionService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestSubmissionService.java)、[TechnicianProfileController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/controller/TechnicianProfileController.java)、[TechnicianDirectoryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/service/TechnicianDirectoryService.java)、[WorkOrderController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/workorder/controller/WorkOrderController.java)

## reporting 模块

### [AdminWorkspaceController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/controller/AdminWorkspaceController.java)

- 位置：`src/main/java/com/smartfix/reporting/controller/AdminWorkspaceController.java`
- 运行场景：场景06管理员概览、20地图状态、21报表导出。
- 职责：管理员首页聚合报修阶段计数、待派单和待终审任务。
- 使用的项目类型：[SmartFixUserDetails.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SmartFixUserDetails.java)、[RequestStage.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStage.java)、[RequestQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestQueryService.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [CampusMapController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/controller/CampusMapController.java)

- 位置：`src/main/java/com/smartfix/reporting/controller/CampusMapController.java`
- 运行场景：场景06管理员概览、20地图状态、21报表导出。
- 职责：接收所属场景的 HTTP 请求，调用服务并选择页面、跳转或数据响应。
- 使用的项目类型：[SmartFixUserDetails.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SmartFixUserDetails.java)、[FacilityStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/domain/FacilityStatus.java)、[CampusMapService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/CampusMapService.java)、[CampusMapSnapshot.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/dto/CampusMapSnapshot.java)、[CampusMapStatusService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/service/CampusMapStatusService.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [DashboardController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/controller/DashboardController.java)

- 位置：`src/main/java/com/smartfix/reporting/controller/DashboardController.java`
- 运行场景：场景06管理员概览、20地图状态、21报表导出。
- 职责：接收所属场景的 HTTP 请求，调用服务并选择页面、跳转或数据响应。
- 使用的项目类型：[SmartFixUserDetails.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SmartFixUserDetails.java)、[DashboardService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/service/DashboardService.java)、[ReportingPeriodService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/service/ReportingPeriodService.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [ReportController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/controller/ReportController.java)

- 位置：`src/main/java/com/smartfix/reporting/controller/ReportController.java`
- 运行场景：场景06管理员概览、20地图状态、21报表导出。
- 职责：接收所属场景的 HTTP 请求，调用服务并选择页面、跳转或数据响应。
- 使用的项目类型：[InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)、[OperationalReportService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/service/OperationalReportService.java)、[ReportExportService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/service/ReportExportService.java)、[ReportingPeriodService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/service/ReportingPeriodService.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [AdminDashboardResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/dto/AdminDashboardResponse.java)

- 位置：`src/main/java/com/smartfix/reporting/dto/AdminDashboardResponse.java`
- 运行场景：场景06管理员概览、20地图状态、21报表导出。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[DashboardService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/service/DashboardService.java)

### [CampusMapSnapshot.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/dto/CampusMapSnapshot.java)

- 位置：`src/main/java/com/smartfix/reporting/dto/CampusMapSnapshot.java`
- 运行场景：场景06管理员概览、20地图状态、21报表导出。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[CampusMapController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/controller/CampusMapController.java)、[CampusMapStatusService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/service/CampusMapStatusService.java)

### [OperationalReportResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/dto/OperationalReportResponse.java)

- 位置：`src/main/java/com/smartfix/reporting/dto/OperationalReportResponse.java`
- 运行场景：场景06管理员概览、20地图状态、21报表导出。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：[MaintenanceCategory.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceCategory.java)、[RequestStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatus.java)、[UrgencyLevel.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/UrgencyLevel.java)
- 引用它的项目文件：[DashboardService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/service/DashboardService.java)、[OperationalReportService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/service/OperationalReportService.java)、[ReportExportService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/service/ReportExportService.java)

### [TechnicianDashboardResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/dto/TechnicianDashboardResponse.java)

- 位置：`src/main/java/com/smartfix/reporting/dto/TechnicianDashboardResponse.java`
- 运行场景：场景06管理员概览、20地图状态、21报表导出。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[DashboardService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/service/DashboardService.java)

### [CampusMapStatusService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/service/CampusMapStatusService.java)

- 位置：`src/main/java/com/smartfix/reporting/service/CampusMapStatusService.java`
- 运行场景：场景06管理员概览、20地图状态、21报表导出。
- 职责：在 reporting 中组合地理与报修数据，生成地图状态快照。
- 使用的项目类型：[CampusGeographyService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/facility/service/CampusGeographyService.java)、[CampusMapSnapshot.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/dto/CampusMapSnapshot.java)、[RequestStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatus.java)、[RequestReadService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestReadService.java)
- 引用它的项目文件：[CampusMapController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/controller/CampusMapController.java)

### [DashboardService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/service/DashboardService.java)

- 位置：`src/main/java/com/smartfix/reporting/service/DashboardService.java`
- 运行场景：场景06管理员概览、20地图状态、21报表导出。
- 职责：保留的管理员或工程师统计仪表盘数据服务。
- 使用的项目类型：[AdminDashboardResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/dto/AdminDashboardResponse.java)、[OperationalReportResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/dto/OperationalReportResponse.java)、[TechnicianDashboardResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/dto/TechnicianDashboardResponse.java)、[OperationalReportService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/service/OperationalReportService.java)、[TechnicianWorkloadService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/technician/service/TechnicianWorkloadService.java)
- 引用它的项目文件：[DashboardController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/controller/DashboardController.java)

### [OperationalReportService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/service/OperationalReportService.java)

- 位置：`src/main/java/com/smartfix/reporting/service/OperationalReportService.java`
- 运行场景：场景06管理员概览、20地图状态、21报表导出。
- 职责：按日期范围汇总业务统计。
- 使用的项目类型：[InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)、[OperationalReportResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/dto/OperationalReportResponse.java)、[MaintenanceCategory.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/MaintenanceCategory.java)、[RequestStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/RequestStatus.java)、[UrgencyLevel.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/domain/UrgencyLevel.java)、[RequestSnapshotResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/dto/RequestSnapshotResponse.java)、[RequestReadService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/request/service/RequestReadService.java)
- 引用它的项目文件：[ReportController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/controller/ReportController.java)、[DashboardService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/service/DashboardService.java)

### [ReportExportService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/service/ReportExportService.java)

- 位置：`src/main/java/com/smartfix/reporting/service/ReportExportService.java`
- 运行场景：场景06管理员概览、20地图状态、21报表导出。
- 职责：将报表结果输出为 CSV 等导出内容。
- 使用的项目类型：[OperationalReportResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/dto/OperationalReportResponse.java)
- 引用它的项目文件：[ReportController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/controller/ReportController.java)

### [ReportingPeriodService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/service/ReportingPeriodService.java)

- 位置：`src/main/java/com/smartfix/reporting/service/ReportingPeriodService.java`
- 运行场景：场景06管理员概览、20地图状态、21报表导出。
- 职责：把报表日期转为统一时区和查询区间。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[DashboardController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/controller/DashboardController.java)、[ReportController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/reporting/controller/ReportController.java)

## community 模块

### [CommunityProperties.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/config/CommunityProperties.java)

- 位置：`src/main/java/com/smartfix/community/config/CommunityProperties.java`
- 运行场景：场景23社区提问回答、24举报管理。
- 职责：读取或声明所属功能的配置，供启动与运行期组件使用。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[CommunityAnswerService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityAnswerService.java)、[CommunityModerationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityModerationService.java)、[CommunityQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityQueryService.java)、[CommunityQuestionService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityQuestionService.java)

### [AdminCommunityController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/AdminCommunityController.java)

- 位置：`src/main/java/com/smartfix/community/controller/AdminCommunityController.java`
- 运行场景：场景23社区提问回答、24举报管理。
- 职责：接收所属场景的 HTTP 请求，调用服务并选择页面、跳转或数据响应。
- 使用的项目类型：[SmartFixUserDetails.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SmartFixUserDetails.java)、[BusinessConflictException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/BusinessConflictException.java)、[InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)、[CommunityReportResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/CommunityReportResponse.java)、[ReportView.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/ReportView.java)、[ResolveReportCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/ResolveReportCommand.java)、[CommunityModerationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityModerationService.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [CommunityAnswerController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityAnswerController.java)

- 位置：`src/main/java/com/smartfix/community/controller/CommunityAnswerController.java`
- 运行场景：场景23社区提问回答、24举报管理。
- 职责：接收所属场景的 HTTP 请求，调用服务并选择页面、跳转或数据响应。
- 使用的项目类型：[SmartFixUserDetails.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SmartFixUserDetails.java)、[BusinessConflictException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/BusinessConflictException.java)、[InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)、[CommunityDetailPageModel.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityDetailPageModel.java)、[CommunityQuestionController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityQuestionController.java)、[AnswerFormCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/AnswerFormCommand.java)、[OwnAnswerResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/OwnAnswerResponse.java)、[CommunityAnswerService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityAnswerService.java)、[CommunityQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityQueryService.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [CommunityDetailPageModel.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityDetailPageModel.java)

- 位置：`src/main/java/com/smartfix/community/controller/CommunityDetailPageModel.java`
- 运行场景：场景23社区提问回答、24举报管理。
- 职责：集中准备问题详情页的 Model，供问题与回答入口复用。
- 使用的项目类型：[CommunityReportReason.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityReportReason.java)、[CommunityQuestionDetailResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/CommunityQuestionDetailResponse.java)、[CommunityQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityQueryService.java)
- 引用它的项目文件：[CommunityAnswerController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityAnswerController.java)、[CommunityQuestionController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityQuestionController.java)

### [CommunityQuestionController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityQuestionController.java)

- 位置：`src/main/java/com/smartfix/community/controller/CommunityQuestionController.java`
- 运行场景：场景23社区提问回答、24举报管理。
- 职责：接收所属场景的 HTTP 请求，调用服务并选择页面、跳转或数据响应。
- 使用的项目类型：[SmartFixUserDetails.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SmartFixUserDetails.java)、[InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)、[ResourceNotFoundException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/ResourceNotFoundException.java)、[CommunityDetailPageModel.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityDetailPageModel.java)、[CommunityCategory.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityCategory.java)、[AnswerFormCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/AnswerFormCommand.java)、[CommunityQuestionDetailResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/CommunityQuestionDetailResponse.java)、[CommunityQuestionSummaryResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/CommunityQuestionSummaryResponse.java)、[MineTab.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/MineTab.java)、[MyAnswerResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/MyAnswerResponse.java)、[QuestionFilter.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/QuestionFilter.java)、[QuestionFormCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/QuestionFormCommand.java)、[CommunityQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityQueryService.java)、[CommunityQuestionService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityQuestionService.java)
- 引用它的项目文件：[CommunityAnswerController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityAnswerController.java)

### [CommunityReportController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityReportController.java)

- 位置：`src/main/java/com/smartfix/community/controller/CommunityReportController.java`
- 运行场景：场景23社区提问回答、24举报管理。
- 职责：接收所属场景的 HTTP 请求，调用服务并选择页面、跳转或数据响应。
- 使用的项目类型：[SmartFixUserDetails.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SmartFixUserDetails.java)、[BusinessConflictException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/BusinessConflictException.java)、[ReportContentCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/ReportContentCommand.java)、[CommunityModerationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityModerationService.java)、[CommunityQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityQueryService.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [CommunityAnswer.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityAnswer.java)

- 位置：`src/main/java/com/smartfix/community/domain/CommunityAnswer.java`
- 运行场景：场景23社区提问回答、24举报管理。
- 职责：保存所属业务对象的状态和规则，由服务创建或修改，并由仓库持久化。
- 使用的项目类型：[BusinessConflictException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/BusinessConflictException.java)、[CommunityContentStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityContentStatus.java)
- 引用它的项目文件：[AnswerFormCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/AnswerFormCommand.java)、[CommunityAnswerRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/repository/CommunityAnswerRepository.java)、[CommunityAnswerService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityAnswerService.java)、[CommunityModerationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityModerationService.java)、[CommunityQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityQueryService.java)

### [CommunityCategory.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityCategory.java)

- 位置：`src/main/java/com/smartfix/community/domain/CommunityCategory.java`
- 运行场景：场景23社区提问回答、24举报管理。
- 职责：限制该业务概念的合法取值，供规则判断、持久化或页面选项复用。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[CommunityQuestionController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityQuestionController.java)、[CommunityQuestion.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityQuestion.java)、[CommunityQuestionDetailResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/CommunityQuestionDetailResponse.java)、[CommunityQuestionSummaryResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/CommunityQuestionSummaryResponse.java)、[QuestionFormCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/QuestionFormCommand.java)、[CommunityQuestionRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/repository/CommunityQuestionRepository.java)、[CommunityQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityQueryService.java)

### [CommunityContentStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityContentStatus.java)

- 位置：`src/main/java/com/smartfix/community/domain/CommunityContentStatus.java`
- 运行场景：场景23社区提问回答、24举报管理。
- 职责：限制该业务概念的合法取值，供规则判断、持久化或页面选项复用。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[CommunityAnswer.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityAnswer.java)、[CommunityQuestion.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityQuestion.java)、[CommunityAnswerResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/CommunityAnswerResponse.java)、[CommunityQuestionDetailResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/CommunityQuestionDetailResponse.java)、[CommunityQuestionSummaryResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/CommunityQuestionSummaryResponse.java)、[CommunityReportResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/CommunityReportResponse.java)、[MyAnswerResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/MyAnswerResponse.java)、[CommunityContentHiddenEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/event/CommunityContentHiddenEvent.java)、[CommunityAnswerRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/repository/CommunityAnswerRepository.java)、[CommunityQuestionRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/repository/CommunityQuestionRepository.java)、[CommunityAnswerService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityAnswerService.java)、[CommunityModerationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityModerationService.java)、[CommunityNotificationListener.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/listener/CommunityNotificationListener.java)

### [CommunityContentType.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityContentType.java)

- 位置：`src/main/java/com/smartfix/community/domain/CommunityContentType.java`
- 运行场景：场景23社区提问回答、24举报管理。
- 职责：限制该业务概念的合法取值，供规则判断、持久化或页面选项复用。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[CommunityReport.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityReport.java)、[CommunityReportResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/CommunityReportResponse.java)、[CommunityContentHiddenEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/event/CommunityContentHiddenEvent.java)、[CommunityAnswerService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityAnswerService.java)、[CommunityModerationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityModerationService.java)

### [CommunityQuestion.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityQuestion.java)

- 位置：`src/main/java/com/smartfix/community/domain/CommunityQuestion.java`
- 运行场景：场景23社区提问回答、24举报管理。
- 职责：保存所属业务对象的状态和规则，由服务创建或修改，并由仓库持久化。
- 使用的项目类型：[BusinessConflictException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/BusinessConflictException.java)、[CommunityCategory.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityCategory.java)、[CommunityContentStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityContentStatus.java)
- 引用它的项目文件：[QuestionFormCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/QuestionFormCommand.java)、[CommunityQuestionRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/repository/CommunityQuestionRepository.java)、[CommunityAnswerService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityAnswerService.java)、[CommunityModerationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityModerationService.java)、[CommunityQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityQueryService.java)、[CommunityQuestionService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityQuestionService.java)

### [CommunityReport.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityReport.java)

- 位置：`src/main/java/com/smartfix/community/domain/CommunityReport.java`
- 运行场景：场景23社区提问回答、24举报管理。
- 职责：保存所属业务对象的状态和规则，由服务创建或修改，并由仓库持久化。
- 使用的项目类型：[CommunityContentType.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityContentType.java)、[CommunityReportReason.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityReportReason.java)、[CommunityReportStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityReportStatus.java)
- 引用它的项目文件：[ReportContentCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/ReportContentCommand.java)、[ResolveReportCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/ResolveReportCommand.java)、[CommunityReportRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/repository/CommunityReportRepository.java)、[CommunityModerationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityModerationService.java)

### [CommunityReportReason.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityReportReason.java)

- 位置：`src/main/java/com/smartfix/community/domain/CommunityReportReason.java`
- 运行场景：场景23社区提问回答、24举报管理。
- 职责：限制该业务概念的合法取值，供规则判断、持久化或页面选项复用。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[CommunityDetailPageModel.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityDetailPageModel.java)、[CommunityReport.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityReport.java)、[CommunityReportResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/CommunityReportResponse.java)、[ReportContentCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/ReportContentCommand.java)

### [CommunityReportStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityReportStatus.java)

- 位置：`src/main/java/com/smartfix/community/domain/CommunityReportStatus.java`
- 运行场景：场景23社区提问回答、24举报管理。
- 职责：限制该业务概念的合法取值，供规则判断、持久化或页面选项复用。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[CommunityReport.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityReport.java)、[CommunityReportResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/CommunityReportResponse.java)、[ResolveReportCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/ResolveReportCommand.java)、[CommunityReportRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/repository/CommunityReportRepository.java)、[CommunityModerationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityModerationService.java)

### [AnswerFormCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/AnswerFormCommand.java)

- 位置：`src/main/java/com/smartfix/community/dto/AnswerFormCommand.java`
- 运行场景：场景23社区提问回答、24举报管理。
- 职责：承载该操作的输入数据或表单值，交给 Controller 和 Service 校验、执行。
- 使用的项目类型：[CommunityAnswer.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityAnswer.java)
- 引用它的项目文件：[CommunityAnswerController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityAnswerController.java)、[CommunityQuestionController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityQuestionController.java)、[CommunityAnswerService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityAnswerService.java)

### [CommunityAnswerResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/CommunityAnswerResponse.java)

- 位置：`src/main/java/com/smartfix/community/dto/CommunityAnswerResponse.java`
- 运行场景：场景23社区提问回答、24举报管理。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：[CommunityContentStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityContentStatus.java)
- 引用它的项目文件：[CommunityQuestionDetailResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/CommunityQuestionDetailResponse.java)、[CommunityQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityQueryService.java)

### [CommunityQuestionDetailResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/CommunityQuestionDetailResponse.java)

- 位置：`src/main/java/com/smartfix/community/dto/CommunityQuestionDetailResponse.java`
- 运行场景：场景23社区提问回答、24举报管理。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：[CommunityCategory.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityCategory.java)、[CommunityContentStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityContentStatus.java)、[CommunityAnswerResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/CommunityAnswerResponse.java)
- 引用它的项目文件：[CommunityDetailPageModel.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityDetailPageModel.java)、[CommunityQuestionController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityQuestionController.java)、[CommunityQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityQueryService.java)

### [CommunityQuestionSummaryResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/CommunityQuestionSummaryResponse.java)

- 位置：`src/main/java/com/smartfix/community/dto/CommunityQuestionSummaryResponse.java`
- 运行场景：场景23社区提问回答、24举报管理。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：[CommunityCategory.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityCategory.java)、[CommunityContentStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityContentStatus.java)
- 引用它的项目文件：[CommunityQuestionController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityQuestionController.java)、[CommunityQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityQueryService.java)

### [CommunityReportResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/CommunityReportResponse.java)

- 位置：`src/main/java/com/smartfix/community/dto/CommunityReportResponse.java`
- 运行场景：场景23社区提问回答、24举报管理。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：[CommunityContentStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityContentStatus.java)、[CommunityContentType.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityContentType.java)、[CommunityReportReason.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityReportReason.java)、[CommunityReportStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityReportStatus.java)
- 引用它的项目文件：[AdminCommunityController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/AdminCommunityController.java)、[CommunityModerationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityModerationService.java)

### [MineTab.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/MineTab.java)

- 位置：`src/main/java/com/smartfix/community/dto/MineTab.java`
- 运行场景：场景23社区提问回答、24举报管理。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[CommunityQuestionController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityQuestionController.java)

### [MyAnswerResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/MyAnswerResponse.java)

- 位置：`src/main/java/com/smartfix/community/dto/MyAnswerResponse.java`
- 运行场景：场景23社区提问回答、24举报管理。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：[CommunityContentStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityContentStatus.java)
- 引用它的项目文件：[CommunityQuestionController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityQuestionController.java)、[CommunityQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityQueryService.java)

### [OwnAnswerResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/OwnAnswerResponse.java)

- 位置：`src/main/java/com/smartfix/community/dto/OwnAnswerResponse.java`
- 运行场景：场景23社区提问回答、24举报管理。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[CommunityAnswerController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityAnswerController.java)、[CommunityQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityQueryService.java)

### [QuestionFilter.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/QuestionFilter.java)

- 位置：`src/main/java/com/smartfix/community/dto/QuestionFilter.java`
- 运行场景：场景23社区提问回答、24举报管理。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[CommunityQuestionController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityQuestionController.java)、[CommunityQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityQueryService.java)

### [QuestionFormCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/QuestionFormCommand.java)

- 位置：`src/main/java/com/smartfix/community/dto/QuestionFormCommand.java`
- 运行场景：场景23社区提问回答、24举报管理。
- 职责：承载该操作的输入数据或表单值，交给 Controller 和 Service 校验、执行。
- 使用的项目类型：[CommunityCategory.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityCategory.java)、[CommunityQuestion.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityQuestion.java)
- 引用它的项目文件：[CommunityQuestionController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityQuestionController.java)、[CommunityQuestionService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityQuestionService.java)

### [ReportContentCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/ReportContentCommand.java)

- 位置：`src/main/java/com/smartfix/community/dto/ReportContentCommand.java`
- 运行场景：场景23社区提问回答、24举报管理。
- 职责：承载该操作的输入数据或表单值，交给 Controller 和 Service 校验、执行。
- 使用的项目类型：[CommunityReport.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityReport.java)、[CommunityReportReason.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityReportReason.java)
- 引用它的项目文件：[CommunityReportController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityReportController.java)、[CommunityModerationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityModerationService.java)

### [ReportView.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/ReportView.java)

- 位置：`src/main/java/com/smartfix/community/dto/ReportView.java`
- 运行场景：场景23社区提问回答、24举报管理。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[AdminCommunityController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/AdminCommunityController.java)、[CommunityModerationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityModerationService.java)

### [ResolveReportCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/ResolveReportCommand.java)

- 位置：`src/main/java/com/smartfix/community/dto/ResolveReportCommand.java`
- 运行场景：场景23社区提问回答、24举报管理。
- 职责：承载该操作的输入数据或表单值，交给 Controller 和 Service 校验、执行。
- 使用的项目类型：[CommunityReport.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityReport.java)、[CommunityReportStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityReportStatus.java)
- 引用它的项目文件：[AdminCommunityController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/AdminCommunityController.java)、[CommunityModerationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityModerationService.java)

### [CommunityAnswerAcceptedEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/event/CommunityAnswerAcceptedEvent.java)

- 位置：`src/main/java/com/smartfix/community/event/CommunityAnswerAcceptedEvent.java`
- 运行场景：场景23社区提问回答、24举报管理。
- 职责：承载已经发生的业务事实；事件发布者与监听者通过该类型协作。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[CommunityAnswerService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityAnswerService.java)、[CommunityNotificationListener.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/listener/CommunityNotificationListener.java)

### [CommunityAnswerCreatedEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/event/CommunityAnswerCreatedEvent.java)

- 位置：`src/main/java/com/smartfix/community/event/CommunityAnswerCreatedEvent.java`
- 运行场景：场景23社区提问回答、24举报管理。
- 职责：承载已经发生的业务事实；事件发布者与监听者通过该类型协作。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[CommunityAnswerService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityAnswerService.java)、[CommunityNotificationListener.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/listener/CommunityNotificationListener.java)

### [CommunityContentHiddenEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/event/CommunityContentHiddenEvent.java)

- 位置：`src/main/java/com/smartfix/community/event/CommunityContentHiddenEvent.java`
- 运行场景：场景23社区提问回答、24举报管理。
- 职责：承载已经发生的业务事实；事件发布者与监听者通过该类型协作。
- 使用的项目类型：[CommunityContentStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityContentStatus.java)、[CommunityContentType.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityContentType.java)
- 引用它的项目文件：[CommunityAnswerService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityAnswerService.java)、[CommunityModerationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityModerationService.java)、[CommunityNotificationListener.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/listener/CommunityNotificationListener.java)

### [CommunityAnswerRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/repository/CommunityAnswerRepository.java)

- 位置：`src/main/java/com/smartfix/community/repository/CommunityAnswerRepository.java`
- 运行场景：场景23社区提问回答、24举报管理。
- 职责：为所属业务对象提供数据库查询与持久化；通常由本模块服务调用。
- 使用的项目类型：[CommunityAnswer.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityAnswer.java)、[CommunityContentStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityContentStatus.java)
- 引用它的项目文件：[CommunityAnswerService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityAnswerService.java)、[CommunityModerationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityModerationService.java)、[CommunityQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityQueryService.java)

### [CommunityQuestionRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/repository/CommunityQuestionRepository.java)

- 位置：`src/main/java/com/smartfix/community/repository/CommunityQuestionRepository.java`
- 运行场景：场景23社区提问回答、24举报管理。
- 职责：为所属业务对象提供数据库查询与持久化；通常由本模块服务调用。
- 使用的项目类型：[CommunityCategory.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityCategory.java)、[CommunityContentStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityContentStatus.java)、[CommunityQuestion.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityQuestion.java)
- 引用它的项目文件：[CommunityAnswerService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityAnswerService.java)、[CommunityModerationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityModerationService.java)、[CommunityQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityQueryService.java)、[CommunityQuestionService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityQuestionService.java)

### [CommunityReportRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/repository/CommunityReportRepository.java)

- 位置：`src/main/java/com/smartfix/community/repository/CommunityReportRepository.java`
- 运行场景：场景23社区提问回答、24举报管理。
- 职责：为所属业务对象提供数据库查询与持久化；通常由本模块服务调用。
- 使用的项目类型：[CommunityReport.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityReport.java)、[CommunityReportStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityReportStatus.java)
- 引用它的项目文件：[CommunityModerationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityModerationService.java)

### [CommunityAccessGuard.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityAccessGuard.java)

- 位置：`src/main/java/com/smartfix/community/service/CommunityAccessGuard.java`
- 运行场景：场景23社区提问回答、24举报管理。
- 职责：统一检查社区身份、作者归属和操作资格。
- 使用的项目类型：[ResourceNotFoundException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/ResourceNotFoundException.java)、[AccountStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/AccountStatus.java)、[Role.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/Role.java)、[UserAccessResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/dto/UserAccessResponse.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)
- 引用它的项目文件：[CommunityAnswerService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityAnswerService.java)、[CommunityModerationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityModerationService.java)、[CommunityQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityQueryService.java)、[CommunityQuestionService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityQuestionService.java)

### [CommunityAnswerService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityAnswerService.java)

- 位置：`src/main/java/com/smartfix/community/service/CommunityAnswerService.java`
- 运行场景：场景23社区提问回答、24举报管理。
- 职责：回答写入、编辑、撤回和采纳相关业务。
- 使用的项目类型：[BusinessConflictException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/BusinessConflictException.java)、[InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)、[ResourceNotFoundException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/ResourceNotFoundException.java)、[CommunityProperties.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/config/CommunityProperties.java)、[CommunityAnswer.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityAnswer.java)、[CommunityContentStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityContentStatus.java)、[CommunityContentType.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityContentType.java)、[CommunityQuestion.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityQuestion.java)、[AnswerFormCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/AnswerFormCommand.java)、[CommunityAnswerAcceptedEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/event/CommunityAnswerAcceptedEvent.java)、[CommunityAnswerCreatedEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/event/CommunityAnswerCreatedEvent.java)、[CommunityContentHiddenEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/event/CommunityContentHiddenEvent.java)、[CommunityAnswerRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/repository/CommunityAnswerRepository.java)、[CommunityQuestionRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/repository/CommunityQuestionRepository.java)、[CommunityAccessGuard.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityAccessGuard.java)
- 引用它的项目文件：[CommunityAnswerController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityAnswerController.java)

### [CommunityModerationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityModerationService.java)

- 位置：`src/main/java/com/smartfix/community/service/CommunityModerationService.java`
- 运行场景：场景23社区提问回答、24举报管理。
- 职责：处理举报、隐藏或恢复内容，并关联通知与审计。
- 使用的项目类型：[AuditService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/audit/service/AuditService.java)、[BusinessConflictException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/BusinessConflictException.java)、[InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)、[ResourceNotFoundException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/ResourceNotFoundException.java)、[CommunityProperties.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/config/CommunityProperties.java)、[CommunityAnswer.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityAnswer.java)、[CommunityContentStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityContentStatus.java)、[CommunityContentType.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityContentType.java)、[CommunityQuestion.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityQuestion.java)、[CommunityReport.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityReport.java)、[CommunityReportStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityReportStatus.java)、[CommunityReportResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/CommunityReportResponse.java)、[ReportContentCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/ReportContentCommand.java)、[ReportView.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/ReportView.java)、[ResolveReportCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/ResolveReportCommand.java)、[CommunityContentHiddenEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/event/CommunityContentHiddenEvent.java)、[CommunityAnswerRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/repository/CommunityAnswerRepository.java)、[CommunityQuestionRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/repository/CommunityQuestionRepository.java)、[CommunityReportRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/repository/CommunityReportRepository.java)、[CommunityAccessGuard.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityAccessGuard.java)
- 引用它的项目文件：[AdminCommunityController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/AdminCommunityController.java)、[CommunityReportController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityReportController.java)

### [CommunityQueryService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityQueryService.java)

- 位置：`src/main/java/com/smartfix/community/service/CommunityQueryService.java`
- 运行场景：场景23社区提问回答、24举报管理。
- 职责：列表筛选、问题详情、我的问题与回答等读取。
- 使用的项目类型：[InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)、[ResourceNotFoundException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/ResourceNotFoundException.java)、[CommunityProperties.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/config/CommunityProperties.java)、[CommunityAnswer.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityAnswer.java)、[CommunityCategory.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityCategory.java)、[CommunityQuestion.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityQuestion.java)、[CommunityAnswerResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/CommunityAnswerResponse.java)、[CommunityQuestionDetailResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/CommunityQuestionDetailResponse.java)、[CommunityQuestionSummaryResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/CommunityQuestionSummaryResponse.java)、[MyAnswerResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/MyAnswerResponse.java)、[OwnAnswerResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/OwnAnswerResponse.java)、[QuestionFilter.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/QuestionFilter.java)、[CommunityAnswerRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/repository/CommunityAnswerRepository.java)、[CommunityQuestionRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/repository/CommunityQuestionRepository.java)、[CommunityAccessGuard.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityAccessGuard.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)
- 引用它的项目文件：[CommunityAnswerController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityAnswerController.java)、[CommunityDetailPageModel.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityDetailPageModel.java)、[CommunityQuestionController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityQuestionController.java)、[CommunityReportController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityReportController.java)

### [CommunityQuestionService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityQuestionService.java)

- 位置：`src/main/java/com/smartfix/community/service/CommunityQuestionService.java`
- 运行场景：场景23社区提问回答、24举报管理。
- 职责：创建、编辑、撤回问题，并处理问题相关操作。
- 使用的项目类型：[BusinessConflictException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/BusinessConflictException.java)、[InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)、[ResourceNotFoundException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/ResourceNotFoundException.java)、[CommunityProperties.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/config/CommunityProperties.java)、[CommunityQuestion.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/domain/CommunityQuestion.java)、[QuestionFormCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/dto/QuestionFormCommand.java)、[CommunityQuestionRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/repository/CommunityQuestionRepository.java)、[CommunityAccessGuard.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityAccessGuard.java)
- 引用它的项目文件：[CommunityQuestionController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/controller/CommunityQuestionController.java)

## announcement 模块

### [AnnouncementController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/controller/AnnouncementController.java)

- 位置：`src/main/java/com/smartfix/announcement/controller/AnnouncementController.java`
- 运行场景：场景06首页公告；内部实现已学过，本索引保留完整性。
- 职责：接收所属场景的 HTTP 请求，调用服务并选择页面、跳转或数据响应。
- 使用的项目类型：[CreateAnnouncementCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/dto/CreateAnnouncementCommand.java)、[AnnouncementService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/service/AnnouncementService.java)、[SmartFixUserDetails.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SmartFixUserDetails.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [Announcement.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/domain/Announcement.java)

- 位置：`src/main/java/com/smartfix/announcement/domain/Announcement.java`
- 运行场景：场景06首页公告；内部实现已学过，本索引保留完整性。
- 职责：保存所属业务对象的状态和规则，由服务创建或修改，并由仓库持久化。
- 使用的项目类型：[AnnouncementStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/domain/AnnouncementStatus.java)、[BusinessConflictException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/BusinessConflictException.java)、[InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)
- 引用它的项目文件：[AnnouncementResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/dto/AnnouncementResponse.java)、[AnnouncementRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/repository/AnnouncementRepository.java)、[AnnouncementService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/service/AnnouncementService.java)

### [AnnouncementStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/domain/AnnouncementStatus.java)

- 位置：`src/main/java/com/smartfix/announcement/domain/AnnouncementStatus.java`
- 运行场景：场景06首页公告；内部实现已学过，本索引保留完整性。
- 职责：限制该业务概念的合法取值，供规则判断、持久化或页面选项复用。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[Announcement.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/domain/Announcement.java)、[AnnouncementResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/dto/AnnouncementResponse.java)、[AnnouncementRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/repository/AnnouncementRepository.java)、[AnnouncementService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/service/AnnouncementService.java)

### [AnnouncementResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/dto/AnnouncementResponse.java)

- 位置：`src/main/java/com/smartfix/announcement/dto/AnnouncementResponse.java`
- 运行场景：场景06首页公告；内部实现已学过，本索引保留完整性。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：[Announcement.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/domain/Announcement.java)、[AnnouncementStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/domain/AnnouncementStatus.java)
- 引用它的项目文件：[AnnouncementService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/service/AnnouncementService.java)

### [CreateAnnouncementCommand.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/dto/CreateAnnouncementCommand.java)

- 位置：`src/main/java/com/smartfix/announcement/dto/CreateAnnouncementCommand.java`
- 运行场景：场景06首页公告；内部实现已学过，本索引保留完整性。
- 职责：承载该操作的输入数据或表单值，交给 Controller 和 Service 校验、执行。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[AnnouncementController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/controller/AnnouncementController.java)

### [AnnouncementRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/repository/AnnouncementRepository.java)

- 位置：`src/main/java/com/smartfix/announcement/repository/AnnouncementRepository.java`
- 运行场景：场景06首页公告；内部实现已学过，本索引保留完整性。
- 职责：为所属业务对象提供数据库查询与持久化；通常由本模块服务调用。
- 使用的项目类型：[Announcement.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/domain/Announcement.java)、[AnnouncementStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/domain/AnnouncementStatus.java)
- 引用它的项目文件：[AnnouncementService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/service/AnnouncementService.java)

### [AnnouncementService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/service/AnnouncementService.java)

- 位置：`src/main/java/com/smartfix/announcement/service/AnnouncementService.java`
- 运行场景：场景06首页公告；内部实现已学过，本索引保留完整性。
- 职责：参与所属场景的协作类型，具体依赖见下方文件关联。
- 使用的项目类型：[Announcement.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/domain/Announcement.java)、[AnnouncementStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/domain/AnnouncementStatus.java)、[AnnouncementResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/dto/AnnouncementResponse.java)、[AnnouncementRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/repository/AnnouncementRepository.java)、[ResourceNotFoundException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/ResourceNotFoundException.java)
- 引用它的项目文件：[AnnouncementController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/announcement/controller/AnnouncementController.java)、[HomeController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/web/HomeController.java)

## audit 模块

### [AuditController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/audit/controller/AuditController.java)

- 位置：`src/main/java/com/smartfix/audit/controller/AuditController.java`
- 运行场景：场景05账号审计、17投递审计、24社区管理审计；内部实现已学过。
- 职责：接收所属场景的 HTTP 请求，调用服务并选择页面、跳转或数据响应。
- 使用的项目类型：[AuditService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/audit/service/AuditService.java)、[SmartFixUserDetails.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/auth/security/SmartFixUserDetails.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [AuditEntry.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/audit/domain/AuditEntry.java)

- 位置：`src/main/java/com/smartfix/audit/domain/AuditEntry.java`
- 运行场景：场景05账号审计、17投递审计、24社区管理审计；内部实现已学过。
- 职责：保存所属业务对象的状态和规则，由服务创建或修改，并由仓库持久化。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[AuditEntryRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/audit/repository/AuditEntryRepository.java)、[AuditService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/audit/service/AuditService.java)

### [AuditEntryResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/audit/dto/AuditEntryResponse.java)

- 位置：`src/main/java/com/smartfix/audit/dto/AuditEntryResponse.java`
- 运行场景：场景05账号审计、17投递审计、24社区管理审计；内部实现已学过。
- 职责：承载查询、页面或模块间传递的数据；由服务组装，由调用方或模板消费。
- 使用的项目类型：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。
- 引用它的项目文件：[AuditService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/audit/service/AuditService.java)

### [NotificationContactAuditListener.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/audit/listener/NotificationContactAuditListener.java)

- 位置：`src/main/java/com/smartfix/audit/listener/NotificationContactAuditListener.java`
- 运行场景：场景05账号审计、17投递审计、24社区管理审计；内部实现已学过。
- 职责：参与所属场景的协作类型，具体依赖见下方文件关联。
- 使用的项目类型：[AuditService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/audit/service/AuditService.java)、[NotificationContactChangedEvent.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/event/NotificationContactChangedEvent.java)
- 引用它的项目文件：未识别到显式项目类型引用；仍可能由 Spring 发现或框架回调。

### [AuditEntryRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/audit/repository/AuditEntryRepository.java)

- 位置：`src/main/java/com/smartfix/audit/repository/AuditEntryRepository.java`
- 运行场景：场景05账号审计、17投递审计、24社区管理审计；内部实现已学过。
- 职责：为所属业务对象提供数据库查询与持久化；通常由本模块服务调用。
- 使用的项目类型：[AuditEntry.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/audit/domain/AuditEntry.java)
- 引用它的项目文件：[AuditService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/audit/service/AuditService.java)

### [AuditService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/audit/service/AuditService.java)

- 位置：`src/main/java/com/smartfix/audit/service/AuditService.java`
- 运行场景：场景05账号审计、17投递审计、24社区管理审计；内部实现已学过。
- 职责：参与所属场景的协作类型，具体依赖见下方文件关联。
- 使用的项目类型：[AuditEntry.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/audit/domain/AuditEntry.java)、[AuditEntryResponse.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/audit/dto/AuditEntryResponse.java)、[AuditEntryRepository.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/audit/repository/AuditEntryRepository.java)、[InputValidationException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/InputValidationException.java)、[ResourceNotFoundException.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/common/exception/ResourceNotFoundException.java)、[AccountStatus.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/AccountStatus.java)、[Role.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/domain/Role.java)、[UserService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/UserService.java)
- 引用它的项目文件：[AuditController.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/audit/controller/AuditController.java)、[NotificationContactAuditListener.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/audit/listener/NotificationContactAuditListener.java)、[CommunityModerationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/community/service/CommunityModerationService.java)、[DeliveryAdministrationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/DeliveryAdministrationService.java)、[DeliveryWorker.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/notification/service/DeliveryWorker.java)、[SlaPolicyService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/sla/service/SlaPolicyService.java)、[AccountAdministrationService.java](C:/Users/zhour/smartfix/src/main/java/com/smartfix/user/service/AccountAdministrationService.java)
