package uk.co.ceilingcat.rrd.gateways.htmlinputgateway

import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import java.io.Closeable

internal class WebDriverLifecycleController(driver: Driver) : Closeable {

    init {
        System.setProperty(driver.property.text, driver.location.file.canonicalPath)
    }

    private fun List<String>.trim() = this.fold(emptyList<String>()) { acc, curr ->
        acc + curr.trim()
    }

    val webDriver: WebDriver = ChromeDriver(
        ChromeOptions().addArguments(
            driver
                .options
                .text
                .split(",")
                .trim()
        )
    )

    override fun close() {
        webDriver.close()
        webDriver.quit()
    }
}
