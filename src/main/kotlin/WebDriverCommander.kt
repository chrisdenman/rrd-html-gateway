package uk.co.ceilingcat.rrd.gateways.htmlinputgateway

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import uk.co.ceilingcat.rrd.gateways.htmlinputgateway.WebDriverCommanderError.ThrowableCaught
import java.io.Closeable
import java.net.URL
import java.util.concurrent.TimeUnit

sealed class WebDriverCommanderError {
    data class ThrowableCaught(val cause: Throwable) : WebDriverCommanderError()
}

data class WaitDurationSeconds(val magnitude: Long)

class WebDriverCommander(driver: Driver, private val waitDurationSeconds: WaitDurationSeconds) : Closeable {

    private val webDriverLifecycleController: WebDriverLifecycleController = WebDriverLifecycleController(driver)

    private val webDriver = webDriverLifecycleController.webDriver

    fun go(url: URL) = handle { webDriver.get(url.toString()) }

    fun id(id: String): Either<WebDriverCommanderError, WebElement> = handle { findElement(By.id(id)) }

    fun x(xPathExpression: String): Either<WebDriverCommanderError, WebElement> = handle {
        findElement(By.xpath(xPathExpression))
    }

    fun xs(xPathExpression: String): Either<WebDriverCommanderError, List<WebElement>> = handle {
        findElements(By.xpath(xPathExpression))
    }

    override fun close() = webDriverLifecycleController.close()

    private fun <R> handle(block: WebDriver.() -> R): Either<WebDriverCommanderError, R> =
        try {
            Thread.sleep(TimeUnit.SECONDS.toMillis(waitDurationSeconds.magnitude))
            webDriver.block().right()
        } catch (t: Throwable) {
            ThrowableCaught(t).left()
        }
}
