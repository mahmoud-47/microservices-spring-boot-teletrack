from flask import Flask, request, jsonify
from openai import OpenAI
import os
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

app = Flask(__name__)

# Initialize OpenAI client
client = OpenAI(api_key=os.getenv('OPENAI_API_KEY'))

@app.route('/api/insights', methods=['POST'])
def get_insights():
    """
    Generate AI insights from incident report data
    """
    try:
        data = request.get_json()

        total_incidents = data.get('totalIncidents', 0)
        by_status = data.get('byStatus', {})
        by_priority = data.get('byPriority', {})
        avg_resolution = data.get('averageResolutionTimeMinutes', 0)

        # Create prompt for OpenAI
        prompt = f"""
        Analyze the following incident report data and provide actionable insights:

        Total Incidents: {total_incidents}
        Status Breakdown: {by_status}
        Priority Breakdown: {by_priority}
        Average Resolution Time: {avg_resolution:.2f} minutes

        Please provide:
        1. Key observations about incident patterns
        2. Areas of concern (if any)
        3. Recommendations for improvement
        4. Performance indicators

        Format your response as structured insights with bullet points.
        """

        # Call OpenAI API
        response = client.chat.completions.create(
            model="gpt-4o-mini",
            messages=[
                {"role": "system", "content": "You are an expert analyst for telecom incident management systems. Provide concise, actionable insights."},
                {"role": "user", "content": prompt}
            ],
            temperature=0.7,
            max_tokens=500
        )

        insights_text = response.choices[0].message.content

        return jsonify({
            'success': True,
            'insights': insights_text,
            'data': {
                'totalIncidents': total_incidents,
                'byStatus': by_status,
                'byPriority': by_priority,
                'averageResolutionTimeMinutes': avg_resolution
            }
        }), 200

    except Exception as e:
        return jsonify({
            'success': False,
            'error': str(e),
            'message': 'Failed to generate insights'
        }), 500


@app.route('/api/summary', methods=['POST'])
def get_summary():
    """
    Generate AI textual summary of incident patterns
    """
    try:
        data = request.get_json()

        period = data.get('period', '')
        daily_counts = data.get('dailyCounts', {})
        total_incidents = data.get('totalIncidents', 0)
        by_status = data.get('byStatus', {})
        by_priority = data.get('byPriority', {})

        # Calculate trend metrics
        counts = list(daily_counts.values()) if daily_counts else []
        trend_direction = "stable"
        if len(counts) >= 2:
            first_half = sum(counts[:len(counts)//2])
            second_half = sum(counts[len(counts)//2:])
            if second_half > first_half * 1.2:
                trend_direction = "increasing"
            elif second_half < first_half * 0.8:
                trend_direction = "decreasing"

        # Create prompt for OpenAI
        prompt = f"""
        Generate a comprehensive executive summary for the following incident report:

        Period: {period}
        Total Incidents: {total_incidents}
        Daily Incident Counts: {daily_counts}
        Trend Direction: {trend_direction}
        Status Distribution: {by_status}
        Priority Distribution: {by_priority}

        Please provide:
        1. Executive summary (2-3 sentences)
        2. Trend analysis
        3. Notable patterns
        4. Risk assessment
        5. Strategic recommendations

        Write in a professional, business-appropriate tone suitable for management review.
        """

        # Call OpenAI API
        response = client.chat.completions.create(
            model="gpt-4o-mini",
            messages=[
                {"role": "system", "content": "You are a senior telecom operations analyst creating executive reports. Be concise, data-driven, and actionable."},
                {"role": "user", "content": prompt}
            ],
            temperature=0.7,
            max_tokens=700
        )

        summary_text = response.choices[0].message.content

        return jsonify({
            'success': True,
            'summary': summary_text,
            'period': period,
            'trendDirection': trend_direction,
            'metadata': {
                'totalIncidents': total_incidents,
                'dailyAverage': sum(counts) / len(counts) if counts else 0,
                'peakDay': max(daily_counts.items(), key=lambda x: x[1]) if daily_counts else None
            }
        }), 200

    except Exception as e:
        return jsonify({
            'success': False,
            'error': str(e),
            'message': 'Failed to generate summary'
        }), 500


@app.route('/health', methods=['GET'])
def health_check():
    """
    Health check endpoint
    """
    return jsonify({
        'status': 'healthy',
        'service': 'ai-insights-service',
        'version': '1.0.0'
    }), 200


if __name__ == '__main__':
    # Check if API key is set
    if not os.getenv('OPENAI_API_KEY'):
        print("WARNING: OPENAI_API_KEY not found in environment variables")

    app.run(host='0.0.0.0', port=8085, debug=True)