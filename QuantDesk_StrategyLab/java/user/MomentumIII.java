/*
 *  javastock - Java MetaStock parser and Stock Portfolio Simulator
 *  Copyright (C) 2005 Zigabyte Corporation. ALL RIGHTS RESERVED.
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.

 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.

 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package user;

import com.zigabyte.metastock.data.*;
import com.zigabyte.stock.trade.*;
import com.zigabyte.stock.indicator.*;
import com.zigabyte.stock.strategy.*;
import java.util.*;

public class MomentumIII implements TradingStrategy {

  private static final double RISK_LEVEL = 1.03;

  private static final Indicator FastLine = new MovingAverage(13);
  private static final Indicator SlowLine = new MovingAverage(50);
  private static final Indicator High52Week = new MovingMaximum(52*7,true);
  private static final Indicator UpperChannel = new MovingMaximum(3,false);
  private static final Indicator LowerChannel = new MovingMinimum(3,false);
  private static final Indicator DOLLARVOLUME = new MovingDollarVolume(3);
  private final Map<String,Double> previousMaxPriceCache =
    new HashMap<String,Double>();
  private Date lastDateCached = null;
  private final double minCashMaxBuyFraction;

  public MomentumIII(double sizingFactor) {
    this.minCashMaxBuyFraction = 1/sizingFactor;
  }

  /** Calls {@link #placeSellOrders placeSellOrders} then
      {@link #placeBuyOrders placeBuyOrders} **/
  public void placeTradeOrders(final StockMarketHistory histories,
			       final TradingAccount account,
			       final Date date, int daysUntilMarketOpen) {
    placeBuyOrders(histories, account, date);
    placeSellOrders(histories, account, date);
  }

  /** Every trading day, look at the stocks that are in the account (if any),
      and sell next day at the Open if the stock's last Closing price
      13-day moving average is below the 50-day moving average. **/
  protected void placeSellOrders(final StockMarketHistory histories,
				 final TradingAccount account,
				 final Date date) {
    if (!histories.hasTradingData(date))
      return; // not a trading day
      
    for (StockPosition position : account) {
      String symbol = position.getSymbol();
      StockHistory history = histories.get(symbol);
      int item = history.getIndexAtOrBefore(date);
      StockDataPoint sdp = history.get(item);

      if (sdp.getAdjustedClose() < FastLine.compute(history, item)) {
          // sell stock based on fast moving average.
          // * fma works bettern then fma/sma crossing
          // * selling at open works better than Low Stop.
	account.sellStock(symbol, position.getShares(),
			  OrderTiming.NEXT_DAY_OPEN, Double.NaN);
      }
      else {
	  // take profits when reward:risk > 2
          account.sellStock(symbol, position.getShares(),
                            OrderTiming.NEXT_DAY_LIMIT,
                            position.getCostBasis() * 2*RISK_LEVEL);

	  // place stop-loss order
	  account.sellStock(symbol, position.getShares(),
			    OrderTiming.NEXT_DAY_STOP, 
			    position.getCostBasis() / RISK_LEVEL);
      }
    }
  }

  private double momentum(Date date, StockHistory history, int n) {
	  Date base = new Date(date.getTime()- n * 86400000);
          if(history.getIndexAtOrBefore(base) < 0) return 0;
          return history.getAtOrBefore(date).getAdjustedClose()
               / history.getAtOrBefore(base).getAdjustedClose();
  } 

    private int lowestFirst(double a, double b) {
        return a > b ? -1 : (a < b ? 1 : 0);
    }

    private int highestFirst(double a, double b) {
        return -1 * lowestFirst(a,b);
    }

  protected void placeBuyOrders(final StockMarketHistory histories,
				final TradingAccount account,
				final Date date) { 

    // must have at least minCashFraction * accountValue in cash
    if (account.getProjectedCashBalance() <
	this.minCashMaxBuyFraction * 
	(account.getProjectedCashBalance() + account.getProjectedStockValue()))
      return;

    updatePreviousMaxPriceCache(histories, date);

    List<StockHistory> candidates = new ArrayList<StockHistory>();
    for (StockHistory history : histories) {
      int item = history.getIndexAtOrBefore(date);
      if (item >= 0) { 

	// eliminate stocks not making a movingavg crossover today
	if (!(
              FastLine.compute(history, item) > 
              SlowLine.compute(history, item)
           && FastLine.compute(history, item-1) <
              SlowLine.compute(history, item-1))) {
	  continue;
        }
	// keep candidate
	candidates.add(history);
      }
    }
    // 4. sort candidates by dollar volume
    Collections.sort(candidates, new Comparator<StockHistory>() {
      public int compare(StockHistory history1, StockHistory history2) {
	  double dollarVolume1 = getDollarVolume(history1);
	  double dollarVolume2 = getDollarVolume(history2);
	  return (dollarVolume1 < dollarVolume2? 1 :
	         dollarVolume1 > dollarVolume2? -1 : 0);
      }
      private double getDollarVolume(StockHistory history) {
        return DOLLARVOLUME.compute(history,
                                        history.getIndexAtOrBefore(date));
      }
    });
    // keep only top percentile - eliminate illiquid stocks
    candidates = candidates.subList(0, candidates.size() * 3 / 4);


    // sort by 52-week relative strength
    Collections.sort(candidates, new Comparator<StockHistory>() {
      public int compare(StockHistory history1, StockHistory history2) {
          double h1 = history1.getAtOrBefore(date).getAdjustedClose() /
              High52Week.compute(history1,date);
          double h2 = history2.getAtOrBefore(date).getAdjustedClose() /
              High52Week.compute(history2,date);
	  return highestFirst(h1, h2);
      }
    });
    candidates = candidates.subList(0, candidates.size()/2);

    // sort by extreme momentum in either direction
    Collections.sort(candidates, new Comparator<StockHistory>() {
      public int compare(StockHistory history1, StockHistory history2) {
          double mo1 = momentum(date, history1, 20);
          double mo2 = momentum(date, history2, 20);
          return highestFirst(Math.abs(mo1), Math.abs(mo2));
      }
    });

    candidates = candidates.subList(0, candidates.size()/2);

    // sort by volatillity 
    Collections.sort(candidates, new Comparator<StockHistory>() {
      public int compare(StockHistory history1, StockHistory history2) {
          double v1 = UpperChannel.compute(history1, date) /
              LowerChannel.compute(history1, date);
          double v2 = UpperChannel.compute(history2, date) /
              LowerChannel.compute(history2, date);
          return highestFirst(v1, v2);
      }
    });
      // value before new purchases
    double projectedAccountValue = 
	account.getProjectedCashBalance() +
	account.getProjectedStockValue();

    // use only available cash for purchases, do not rely on sales proceeds.
    double cashRemaining = account.getCurrentCashBalance(); 
    
    // how much money to put into each stock purchase
    double positionSize = this.minCashMaxBuyFraction * projectedAccountValue;

    // 5. buy as many stocks as possible
    for (StockHistory history : candidates) {
      StockDataPoint sdp = history.getAtOrBefore(date);
      double projectedPrice = sdp.getAdjustedLow();
      int nShares = (int) (positionSize / projectedPrice);
      double projectedCost =
	nShares * projectedPrice + account.getTradeFees(nShares);

      // must have at least minCashFraction * accountValue in cash
      cashRemaining -= positionSize;
      if (cashRemaining <= -projectedAccountValue) // margin
	break;

      if (nShares > 0) {
	// ok to go into a little debt due to price increase or trade fees.
	account.buyStock(history.getSymbol(), nShares,
			 OrderTiming.NEXT_DAY_LIMIT, 
			 projectedPrice);
      } 
    }
  }
  /** Return the day of the week as an int.
      To test if it is Sunday, use
      <pre>
        Calendar.SUNDAY == dayOfWeek(date)
      </pre> **/
  protected int dayOfWeek(Date date) {
    Calendar calendar = new GregorianCalendar();
    calendar.setTime(date);
    return calendar.get(Calendar.DAY_OF_WEEK);
  }

  /** 
   **/
  private void updatePreviousMaxPriceCache(StockMarketHistory histories,
					   Date untilDate) {
    // set until date to week ago
    Calendar calendar = new GregorianCalendar();
    calendar.setTime(untilDate);
    calendar.add(Calendar.DATE, -7);
    untilDate = calendar.getTime();

    if (this.lastDateCached == null ||
	this.lastDateCached.getTime() > untilDate.getTime()) {
      // restarting, so reset cache
      this.previousMaxPriceCache.clear();
      this.lastDateCached = null;
    }
    for (StockHistory history : histories) {
      int startIndex = (this.lastDateCached == null? 0 :
			history.getIndexAtOrAfter(this.lastDateCached));
      int endIndex = history.getIndexAtOrBefore(untilDate);
      if (startIndex >= 0 && endIndex >= 0) { 
	String symbol = history.getSymbol();
	Double cachedMaxPrice = previousMaxPriceCache.get(symbol);
	double maxPrice = (cachedMaxPrice == null? 0.0 : cachedMaxPrice);

	for (int i = startIndex; i < endIndex; i++)
	  maxPrice = Math.max(maxPrice, history.get(i).getAdjustedHigh());

	previousMaxPriceCache.put(symbol, maxPrice);
      } 
    }
    this.lastDateCached = untilDate;
  }
				   
  /** returns shortClassName+"("+minCashMaxBuyFraction+")" **/
  public String toString() {
    String className = this.getClass().getName();
    String shortName = className.substring(className.lastIndexOf('.')+1);
    return shortName+"("+this.minCashMaxBuyFraction+")";
  }
}
