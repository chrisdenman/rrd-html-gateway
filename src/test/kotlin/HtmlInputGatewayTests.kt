package uk.co.ceilingcat.rrd.gateways.emailoutputgateway

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import uk.co.ceilingcat.rrd.gateways.htmlinputgateway.Driver
import uk.co.ceilingcat.rrd.gateways.htmlinputgateway.PostCode
import uk.co.ceilingcat.rrd.gateways.htmlinputgateway.StartUrl
import uk.co.ceilingcat.rrd.gateways.htmlinputgateway.StreetName
import uk.co.ceilingcat.rrd.gateways.htmlinputgateway.WaitDurationSeconds
import uk.co.ceilingcat.rrd.gateways.htmlinputgateway.createHtmlInputGateway
import java.io.File
import java.net.URL

@TestInstance(PER_CLASS)
internal class HtmlInputGatewayTests {

    companion object {
        private const val streetNameText = "Walkern Road"
        private const val postCodeText = "SG1 3RD"
        private const val startUrlText = "https://services.stevenage.gov.uk/find"
        private const val driverPathOsX = "bin/chromedriver_mac_87"
        private const val driverPathLinux = "bin/chromedriver_linux_87"
        private const val driverProperty = "webdriver.chrome.driver"
        private const val waitDurationSecondsMagnitude = 1L

        val startUrl = StartUrl(URL(startUrlText))
        val streetName = StreetName(streetNameText)
        val postCode = PostCode(postCodeText)
        val waitDurationSeconds = WaitDurationSeconds(waitDurationSecondsMagnitude)

        private val driver: Driver = Driver(
            driverProperty,
            File(
                when {
                    System.getProperty("os.name").toLowerCase().contains("mac") -> driverPathOsX
                    else -> driverPathLinux
                }
            )
        )
    }

    @Test
    fun `That we can obtain the next upcoming service details by scraping HTML from the council's website`() {
        assertTrue(
            createHtmlInputGateway(
                startUrl,
                streetName,
                postCode,
                driver,
                waitDurationSeconds
            ).nextUpcoming().isRight()
        )
    }
}
