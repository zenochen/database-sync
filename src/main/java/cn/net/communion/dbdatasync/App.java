package cn.net.communion.dbdatasync;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.impl.StdSchedulerFactory;

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.TriggerBuilder.newTrigger;
import static org.quartz.JobBuilder.newJob;
import cn.net.communion.dbdatasync.dbhelper.Factory;
import cn.net.communion.dbdatasync.entity.DbInfo;
import cn.net.communion.dbdatasync.entity.JobInfo;
/**
 * 读取xml配置文件
 * @author Administrator
 *
 */
public class App 
{
	
    private DbInfo srcDb;
    private DbInfo destDb;
    private List<JobInfo> jobList;
    private String code;
    private static Logger logger = Logger.getLogger(App.class);
    
    public void init(){
    	
        srcDb = new DbInfo();
        destDb = new DbInfo();
        jobList = new ArrayList<JobInfo>();
        SAXReader reader = new SAXReader();
        try {
        	//读取xml的配置文件名，并获取其里面的节点
            Element root = reader.read("jobs.xml").getRootElement();
            Element src = root.element("source");
            Element dest = root.element("dest");
            Element jobs = root.element("jobs");
            //遍历job即同步的表
            for(Iterator it = jobs.elementIterator("job"); it.hasNext();){
                jobList.add((JobInfo) elementInObject((Element)it.next(), new JobInfo()));
            }
            //
            elementInObject(src, srcDb);
            elementInObject(dest, destDb);
            code = root.element("code").getTextTrim();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public Object elementInObject(Element e, Object o) throws IllegalArgumentException, IllegalAccessException{
        Field[] fields = o.getClass().getDeclaredFields();
        for(int index = 0; index < fields.length; index++){
            fields[index].setAccessible(true);
            fields[index].set(o, e.element(fields[index].getName()).getTextTrim());
        }
        return o;
    }
    
    public void start(){
        for(int index = 0; index < jobList.size(); index++){
            JobInfo jobInfo = jobList.get(index);
            String logTitle = "[" + code + "]" + jobInfo.getName() + " ";
            try{
                SchedulerFactory sf = new StdSchedulerFactory();
                Scheduler sched = sf.getScheduler();
                JobDetail job = newJob(DataTask.class).withIdentity("job-" + jobInfo.getName(), code).build();
                job.getJobDataMap().put("srcDb", srcDb);
                job.getJobDataMap().put("destDb", destDb);
                job.getJobDataMap().put("jobInfo", jobInfo);
                job.getJobDataMap().put("logTitle", logTitle);
                logger.info(jobInfo.getCron());
                CronTrigger trigger = newTrigger()
                                .withIdentity("trigger-" +  jobInfo.getName(), code)
                                .withSchedule(cronSchedule(jobInfo.getCron())).build();
                sched.scheduleJob(job, trigger);
                sched.start();  
            }catch(Exception e){
                logger.info(logTitle + e.getMessage());
                logger.info(logTitle + " run failed");
                continue;
            }
        }
    }
    public static void main(String[] args )
    {
        App app = new App();
        app.init();
        app.start();
        
    }  
   
}
