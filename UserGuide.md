# Installation #

First, install Java 1.6 from http://www.java.com/en/download/manual.jsp

Second, download the app. There are two versions. They only differ by the platform they run on:

For Windows 32-bit:
http://www.mediafire.com/?njumdikq3ay

For Windows 64-bit:
http://www.mediafire.com/?gtqovjnoqnn

# How To Run It #

Double-click on the downloaded jar.

# Usage Tips #

The buttons and menus are just for show right now.Â  Currently, you can try the JavaScript functionality by entering some code into the box below where it says "Screening" on the left side.

Download this: http://www.mediafire.com/?dfen4qnkmmd and put it in <location of jar>/data if you don't want to wait for the stock data to be downloaded by the program. There's currently no way of knowing if it's done downloading the data from the program unless you run the jar from the command prompt. This way is a lot faster.

There are two variables that can be used: stock and history
stock has general information about a stock, and has these functions:
  * prevPrice
  * openPrice
  * lastPrice
  * highPrice
  * lowPrice
  * volume
  * changePrice
  * changePricePercentage
  * lastVolume
  * dividendPerShare
  * dividendYield

history has information on the history for each stock, and has these functions:
  * max(int period) -  period is the number of days from today, so max(3) is the highest price in the last 3 days
  * min(int period)
  * correlation(String symbol) - get the correlation of all other stocks to a specific symbol
  * mean
  * stddev
  * dividendLength - number of days between dividend payments
  * lastDividendPayment

So for example:
history.correlation('DELL') > 0.8 && stock.dividendYield > 1.0

Then press execute. Results will appear in the table at the bottom of the window.

Notes:
  * There's no real indication that the thing is scanning. That'll be fixed later.
  * Stock data has to be downloaded first. You can let the program do this, but this will take a very long time. Otherwise you can download it here: http://www.mediafire.com/?dfen4qnkmmd and put it in  <location of jar>/data.