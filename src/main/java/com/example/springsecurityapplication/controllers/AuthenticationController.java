package com.example.springsecurityapplication.controllers;

import com.example.springsecurityapplication.errors.CustomFieldError;
import com.example.springsecurityapplication.errors.FieldErrorResponse;
import com.example.springsecurityapplication.models.Person;
import com.example.springsecurityapplication.repositories.PersonRepository;
import com.example.springsecurityapplication.responses.LoginResponse;
import com.example.springsecurityapplication.responses.Response;
import com.example.springsecurityapplication.responses.UserInfo;
import com.example.springsecurityapplication.security.PersonDetails;
import com.example.springsecurityapplication.services.PersonDetailsService;
import com.example.springsecurityapplication.services.PersonService;
import com.example.springsecurityapplication.token.JWTTokenHelper;
import com.example.springsecurityapplication.util.PersonValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/authentication")
public class AuthenticationController {
    private final PersonValidator personValidator;
    private final PersonService personService;
    private final PersonDetailsService personDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final PersonRepository personRepository;

    private final AuthenticationManager authenticationManager;
    private final JWTTokenHelper jWTTokenHelper;

    @Autowired
    public AuthenticationController(PersonValidator personValidator, PersonService personService, PersonDetailsService personDetailsService, PasswordEncoder passwordEncoder, PersonRepository personRepository, AuthenticationManager authenticationManager, JWTTokenHelper jWTTokenHelper) {
        this.personValidator = personValidator;
        this.personService = personService;
        this.personDetailsService = personDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.personRepository = personRepository;
        this.authenticationManager = authenticationManager;
        this.jWTTokenHelper = jWTTokenHelper;
    }

//    http://localhost:8081/api/authentication/registration
    /* ?????????????????????? */
    @PostMapping(value = "/registration")
    public FieldErrorResponse resultRegistration(@Valid @RequestBody Person person, BindingResult bindingResult) {

        // ?????????????????? ??????????
        personValidator.validate(person, bindingResult);
        List<CustomFieldError> fieldErrors = new ArrayList<>();
        FieldErrorResponse fieldErrorResponse = new FieldErrorResponse();

        // ???????? ???????? ???????????? - ?????????? ??????????????????
        // TODO ?????????????????? ?????????? ???????????? ???? ????????????????????
        if (bindingResult.hasErrors()) {
            System.out.println("Error");
            List<FieldError> errors = bindingResult.getFieldErrors();
            for (FieldError error : errors ) {
                CustomFieldError fieldError = new CustomFieldError();
                fieldError.setField(error.getField());
                fieldError.setMessage(error.getDefaultMessage());
                System.out.println("field: " + error.getField()
                        + "; message: " + error.getDefaultMessage());
                fieldErrors.add(fieldError);
            }
            fieldErrorResponse.setFieldErrors(fieldErrors);
            return fieldErrorResponse;
        }

        System.out.println("Ok");
        personService.register(person);
        return fieldErrorResponse;
    }

    /* ?????????????????????? */
    @PostMapping(value = "/login")
    public ResponseEntity<?> resultAuthorization(@RequestBody Person person) throws BadCredentialsException, UsernameNotFoundException, InvalidKeySpecException, NoSuchAlgorithmException, AuthenticationException {

        String login = person.getLogin(); // ??????????
        String password = person.getPassword(); // ????????????

        // ???????????????? ???????????? ???????????????????? ???????????????????????? ???? ????????????, ???????? ???? ????????
        UserDetails personFind = personDetailsService.loadUserByUsername(login);

        // ???????????????????????????? -> AuthenticationProvider
        final Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        personFind, password));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserDetails user = (UserDetails) authentication.getPrincipal();
        String jwtToken = jWTTokenHelper.generateToken(user.getUsername());

        LoginResponse response = new LoginResponse();
        response.setToken(jwtToken);    // ???????????????????? ??????????

        // ?????????????????? ??????????
        updateTokenUser(login, jwtToken);

        return ResponseEntity.ok(response);
    }

    /* ?????????? ???? ?????????????? */
    @PostMapping(value = "/logout")
    public void logoutUser(@RequestBody Person person) {
        updateTokenUser(person.getLogin(), "");
    }

    /* ???????????????????? ???????????? ???????????????????????? */
    public void updateTokenUser(String login, String jwtToken) {
        // ?????????????? ???????????? ???????????? ???? ????????????
        Person updatePerson = personRepository.findByLogin(login).orElseThrow();
        updatePerson.setToken(jwtToken); // ?????????????????????????? ?????????? ????????????????
        personRepository.save(updatePerson); // ?????????????????? (??????????????????) ????????????
    }

    /* ?????????????? ???????????? */
    @GetMapping (value = "/token")
    public Response getToken(HttpServletRequest request) {

        try {
            String authToken = jWTTokenHelper.getToken(request);
            Response response = new Response("token", authToken);
            return response;
        } catch (Exception e) {
            return new Response("error","error");
        }
    }

    /* ?????????????? ???????? */
    @GetMapping (value = "/role")
    public Response getRole(HttpServletRequest request) {
        try {
            String authToken = jWTTokenHelper.getToken(request);
            String login = jWTTokenHelper.getUsernameFromToken(authToken);
            Person person = personRepository.findByLogin(login).orElseThrow();
            Response response = new Response("role", person.getRole());
            return response;
        } catch (Exception e) {
            return new Response("error","error");
        }
    }

    /* ?????????????????? ???????????????????? */

    /* ???????????? ???? ???????????????????? */
    @ExceptionHandler(UsernameNotFoundException.class)
    public FieldErrorResponse handleException(UsernameNotFoundException exception) {
        List<CustomFieldError> fieldErrors = new ArrayList<>();
        FieldErrorResponse fieldErrorResponse = new FieldErrorResponse();
        CustomFieldError fieldError = new CustomFieldError();
        fieldError.setField("loginError");
        fieldError.setMessage(exception.getMessage());
        fieldErrors.add(fieldError);
        fieldErrorResponse.setFieldErrors(fieldErrors);
        return fieldErrorResponse;
    }

    /* ???????????????? ???????????? */
    @ExceptionHandler(BadCredentialsException.class)
    public FieldErrorResponse handleException(BadCredentialsException exception) {
        List<CustomFieldError> fieldErrors = new ArrayList<>();
        FieldErrorResponse fieldErrorResponse = new FieldErrorResponse();
        CustomFieldError fieldError = new CustomFieldError();
        fieldError.setField("passwordError");
        fieldError.setMessage(exception.getMessage());
        fieldErrors.add(fieldError);
        fieldErrorResponse.setFieldErrors(fieldErrors);
        return fieldErrorResponse;
    }
}
