package uk.co.ceilingcat.rrd.gateways.htmlinputgateway

import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import java.io.Closeable

class WebDriverLifecycleController(driver: Driver) : Closeable {

    init {
        System.setProperty(driver.property, driver.location.canonicalPath)
    }

    val webDriver: WebDriver = ChromeDriver(
        ChromeOptions()
            .addArguments(
                "start-maximized",
                "disable-infobars",
                "whitelist-ip *",
                "proxy-server=\"direct://\"",
                "proxy-bypass-list=*"
            )
    )

    override fun close() {
        webDriver.close()
        webDriver.quit()
    }
}
