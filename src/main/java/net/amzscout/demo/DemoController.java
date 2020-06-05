package net.amzscout.demo;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {

    @RequestMapping(value = "/", method = RequestMethod.GET)
    @ResponseBody
    @Throttled
    public ResponseEntity empty() {
        return new ResponseEntity(HttpStatus.OK);
    }

}
