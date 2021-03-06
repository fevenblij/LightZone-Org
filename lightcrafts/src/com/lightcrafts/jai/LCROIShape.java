/* Copyright (C) 2005-2011 Fabio Riccardi */

package com.lightcrafts.jai;

import com.lightcrafts.model.Region;
import com.lightcrafts.model.Contour;
import com.lightcrafts.jai.opimage.ShapedMask;

import com.lightcrafts.mediax.jai.*;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.awt.*;
import java.awt.image.Raster;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.geom.NoninvertibleTransformException;

/**
 * Created by IntelliJ IDEA.
 * User: fabio
 * Date: Apr 5, 2005
 * Time: 5:21:51 PM
 * To change this template use File | Settings | File Templates.
 */
public class LCROIShape extends ROIShape {
    public LCROIShape(Region r, AffineTransform transform) {
        super(r.getOuterShape());
        this.transform = transform;
        this.region = r;
    }

    private Region region;
    private AffineTransform transform;

    public AffineTransform getTransform() {
        return transform;
    }

    public Region getRegion() {
        return region;
    }

    public boolean intersects(Rectangle rect) {
        return intersects(new Rectangle2D.Double(rect.x, rect.y, rect.width, rect.height));
    }

    public boolean intersects(Rectangle2D rect) {
        Iterator it = region.getContours().iterator();
        while (it.hasNext()) {
            Contour c = (Contour) it.next();
            AffineTransform combined = transform;
            if (c.getTranslation() != null) {
                combined = AffineTransform.getTranslateInstance(c.getTranslation().getX(), c.getTranslation().getY());
                combined.preConcatenate(transform);
            }
            Rectangle2D translatedRect = rect;
            if (!combined.isIdentity()) {
                try {
                    AffineTransform inverse = combined.createInverse();
                    translatedRect = inverse.createTransformedShape(rect).getBounds2D();
                } catch (NoninvertibleTransformException e) {
                    e.printStackTrace();
                }
            }

            // Take the blur tapering into account
            Rectangle bounds = c.getOuterShape().getBounds();
            bounds.grow((int) (c.getWidth()), (int) (c.getWidth()));

            if (bounds.intersects(translatedRect))
                return true;
        }
        return false;
    }

    public Rectangle getOuterBounds() {
        return ShapedMask.getOuterBounds(region, transform);
    }

    List contours = new LinkedList();

    private synchronized boolean somethingChanged() {
        Iterator it = region.getContours().iterator();

        int i = 0;
        while (it.hasNext()) {
            Contour c = (Contour) it.next();

            if (c != contours.get(i))
                return true;

            if (c.getTranslation() != null) {
                if (contours.size() > (i+1) && c.getTranslation() != contours.get(i+1))
                    return true;
                i+=2;
            } else
                i++;
        }

        if (contours.size() != i)
            return true;

        return false;
    }

    private ShapedMask theMask = null;

    public synchronized Raster getData(Rectangle rect) {
        if (theMask == null || somethingChanged()) {
            /*
                We keep the current configuration around
                to check if something changes in this region.
            */
            Iterator it = region.getContours().iterator();
            while (it.hasNext()) {
                Contour c = (Contour) it.next();
                contours.add(c);

                // if  a contour has a translation
                // put that in the next list slot

                if (c.getTranslation() != null)
                    contours.add(c.getTranslation());
            }

            theMask = new ShapedMask(region, this);
        }

        return theMask.getData(rect);
    }
}
